package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.MailProperties;
import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.CandidateApplicationDto;
import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.FileSizeExceededException;
import com.wbscouting.api.exception.InvalidApplicationException;
import com.wbscouting.api.exception.InvalidFileException;
import com.wbscouting.api.exception.StorageException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.email.EmailService;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateApplicationServiceImpl implements CandidateApplicationService {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    public static final int MIN_PHOTOS = 3;
    public static final int MAX_PHOTOS = 8;

    public static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final String PROTOCOL_CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int PROTOCOL_SUFFIX_LENGTH = 4;
    private static final int MAX_PROTOCOL_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final StorageService storageService;
    private final EmailService emailService;
    private final MailProperties mailProperties;
    private final SupabaseProperties supabaseProperties;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CandidateApplyResponseDto apply(CandidateApplyRequestDto requestDto, List<MultipartFile> photos) {
        log.info("Iniciando processamento de nova candidatura de talento: {}", requestDto.getFullName());

        // 1. Validação estrutural de fotos
        validatePhotos(photos);

        // 2. Validação de data de nascimento e menoridade
        int age = calculateAndValidateAge(requestDto);

        // 3. Resolução de bucket de persistência
        String bucket = resolveBucket();

        // Lista concorrente para controle de compensação (rollback de storage)
        List<String> uploadedPaths = new CopyOnWriteArrayList<>();
        List<UploadedPhoto> uploadedPhotos = new ArrayList<>();

        try {
            // 4. Geração de protocolo único
            String protocol = generateUniqueProtocol();

            // 5. Persistência atômica do talento na entidade canônica Model
            Model model = mapToModelEntity(requestDto, age, protocol);
            model = modelRepository.saveAndFlush(model);
            final UUID modelId = model.getId();
            log.info("Candidatura persistida em models com ID={} e protocolo={}. Iniciando upload de {} fotos...",
                    modelId, protocol, photos.size());

            // 6. Upload concorrente das fotografias para o storage
            List<CompletableFuture<UploadedPhoto>> uploadFutures = new ArrayList<>();
            for (int i = 0; i < photos.size(); i++) {
                final int displayOrder = i + 1;
                final MultipartFile file = photos.get(i);
                final String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
                final String storagePath = String.format("%s/%s-%s", modelId, UUID.randomUUID(), sanitizedFilename);

                uploadFutures.add(CompletableFuture.supplyAsync(() -> {
                    log.debug("Executando upload de foto ordem={} para path='{}'", displayOrder, storagePath);
                    storageService.uploadFile(bucket, storagePath, file);
                    uploadedPaths.add(storagePath);
                    String publicUrl = storageService.getPublicUrl(bucket, storagePath);
                    return new UploadedPhoto(displayOrder, storagePath, publicUrl);
                }));
            }

            // Aguarda a conclusão dos uploads
            CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<UploadedPhoto> future : uploadFutures) {
                uploadedPhotos.add(future.join());
            }

            // Ordena as fotos pela ordem de exibição
            uploadedPhotos.sort(Comparator.comparingInt(UploadedPhoto::displayOrder));

            // 7. Persistência dos registros das fotos na entidade canônica ModelMedia
            List<ModelMedia> mediaEntities = new ArrayList<>();
            for (int i = 0; i < uploadedPhotos.size(); i++) {
                UploadedPhoto upload = uploadedPhotos.get(i);
                boolean isCover = (i == 0);
                ModelMedia media = ModelMedia.builder()
                        .model(model)
                        .mediaType(MediaType.BOOK)
                        .filePath(upload.storagePath())
                        .fileUrl(upload.publicUrl())
                        .displayOrder(upload.displayOrder())
                        .isCover(isCover)
                        .isActive(true)
                        .build();
                mediaEntities.add(media);
            }

            modelMediaRepository.saveAll(mediaEntities);

            // Sincroniza foto de capa primária no modelo
            if (!uploadedPhotos.isEmpty()) {
                model.setPrimaryPhotoUrl(uploadedPhotos.get(0).publicUrl());
                modelRepository.save(model);
            }

            // 8. Notificação por e-mail assíncrona pós-commit
            CandidateApplicationDto applicationDto = CandidateApplicationDto.fromModel(model);
            String agencyEmail = mailProperties.getAgencyNotificationEmail();
            schedulePostCommitEmail(agencyEmail, applicationDto);

            log.info("Candidatura concluída com sucesso para ID={} Protocolo={}", modelId, protocol);

            return CandidateApplyResponseDto.builder()
                    .candidateId(modelId)
                    .id(modelId)
                    .protocol(protocol)
                    .status("PENDING")
                    .message("Candidatura recebida com sucesso.")
                    .submittedAt(Instant.now())
                    .build();

        } catch (Exception ex) {
            log.error("Erro detectado durante submissão da candidatura. Iniciando compensação de arquivos no storage...", ex);
            compensateUploadedFiles(bucket, uploadedPaths);

            Throwable root = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;

            String motivo = (root.getMessage() != null && !root.getMessage().isBlank())
                    ? root.getMessage()
                    : "Erro interno ao processar a candidatura. Contate a equipe.";

            if (root instanceof InvalidApplicationException iae) {
                throw iae;
            }
            if (root instanceof InvalidFileException ife) {
                throw ife;
            }
            if (root instanceof FileSizeExceededException fse) {
                throw fse;
            }
            if (root instanceof StorageException se) {
                log.warn("[APPLY] StorageException convertida para IllegalArgumentException: {}", se.getMessage());
                throw new IllegalArgumentException("Não foi possível concluir a candidatura. " + motivo);
            }

            log.warn("[APPLY] Excecao genérica convertida para IllegalArgumentException: {}", motivo);
            throw new IllegalArgumentException("Não foi possível concluir a candidatura. " + motivo);
        }
    }

    private void validatePhotos(List<MultipartFile> photos) {
        if (photos == null || photos.size() < MIN_PHOTOS || photos.size() > MAX_PHOTOS) {
            throw new InvalidApplicationException(String.format(
                    "A submissão de candidatura requer o envio de no mínimo %d e no máximo %d fotos. Quantidade enviada: %d",
                    MIN_PHOTOS, MAX_PHOTOS, (photos != null ? photos.size() : 0)
            ));
        }

        for (int i = 0; i < photos.size(); i++) {
            MultipartFile file = photos.get(i);
            if (file == null || file.isEmpty()) {
                throw new InvalidApplicationException(String.format("A foto na posição %d está vazia.", i + 1));
            }

            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType) || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase().trim())) {
                throw new InvalidApplicationException(String.format(
                        "Tipo de arquivo não permitido para a foto '%s': '%s'. Permitidos apenas JPEG, PNG e WEBP.",
                        file.getOriginalFilename(), contentType
                ));
            }

            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new InvalidApplicationException(String.format(
                        "A foto '%s' excede o limite máximo permitido de 5 MB. Tamanho enviado: %.2f MB",
                        file.getOriginalFilename(), file.getSize() / (1024.0 * 1024.0)
                ));
            }
        }
    }

    private int calculateAndValidateAge(CandidateApplyRequestDto dto) {
        LocalDate birthDate = dto.getBirthDate();
        LocalDate now = LocalDate.now();

        if (birthDate.isAfter(now)) {
            throw new InvalidApplicationException("A data de nascimento deve ser uma data no passado.");
        }

        int age = Period.between(birthDate, now).getYears();
        if (age < 0) {
            throw new InvalidApplicationException("Idade calculada inválida para a data de nascimento informada.");
        }

        if (age < 18) {
            boolean hasGuardian = StringUtils.hasText(dto.getResolvedGuardianName());
            boolean hasContact = StringUtils.hasText(dto.getResolvedGuardianContact());

            if (!hasGuardian || !hasContact) {
                throw new InvalidApplicationException(
                        "Candidatos menores de 18 anos devem informar obrigatoriamente o nome e contato do responsável legal."
                );
            }
        }

        return age;
    }

    private Model mapToModelEntity(CandidateApplyRequestDto dto, int age, String protocol) {
        GenderType gender = dto.getGender() != null ? dto.getGender() : GenderType.FEMALE;

        return Model.builder()
                .stageName(dto.getFullName().trim())
                .fullName(dto.getFullName().trim())
                .protocol(protocol)
                .email(dto.getEmail().trim().toLowerCase())
                .phone(dto.getPhone().trim())
                .birthDate(dto.getBirthDate())
                .age(age)
                .gender(gender)
                .heightCm(dto.getHeightCm())
                .weightKg(dto.getWeightKg())
                .city(dto.getCity().trim())
                .state(dto.getState().trim())
                .bustChestCm(dto.getBustChestCm())
                .waistCm(dto.getWaistCm())
                .hipsCm(dto.getHipsCm())
                .dressSize(dto.getDressSize())
                .shoeSize(dto.getShoeSize())
                .hairColor(dto.getHairColor())
                .eyesColor(dto.getEyesColor())
                .instagramUrl(dto.getInstagramHandle())
                .legalGuardianName(dto.getResolvedGuardianName())
                .legalGuardianContact(dto.getResolvedGuardianContact())
                .status(TalentLifecycleStatus.APPLICANT_PENDING)
                .lgpdAccepted(true)
                .isActive(true)
                .isStar(false)
                .isFeaturedHome(false)
                .build();
    }

    private String generateUniqueProtocol() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        int attempts = 0;
        while (attempts < MAX_PROTOCOL_ATTEMPTS) {
            String candidate = String.format("WB-%s-%s", datePart, generateRandomSuffix());
            if (!modelRepository.existsByProtocol(candidate)) {
                return candidate;
            }
            attempts++;
            log.warn("[PROTOCOL] Tentativa {} - protocolo '{}' já existe. Regenerando...", attempts, candidate);
        }
        return String.format("WB-%s-%06d", datePart, RANDOM.nextInt(1000000));
    }

    private String generateRandomSuffix() {
        StringBuilder sb = new StringBuilder(PROTOCOL_SUFFIX_LENGTH);
        final int size = PROTOCOL_CHARSET.length();
        for (int i = 0; i < PROTOCOL_SUFFIX_LENGTH; i++) {
            sb.append(PROTOCOL_CHARSET.charAt(RANDOM.nextInt(size)));
        }
        return sb.toString();
    }

    private String resolveBucket() {
        if (supabaseProperties != null) {
            String bucket = supabaseProperties.resolveBucketCandidates();
            if (StringUtils.hasText(bucket)) {
                return bucket;
            }
        }
        return "models-media";
    }

    private void schedulePostCommitEmail(String agencyEmail, CandidateApplicationDto applicationDto) {
        if (!StringUtils.hasText(agencyEmail)) {
            log.warn("E-mail de notificação da agência não configurado. Notificação assíncrona ignorada.");
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Transação confirmada no banco. Disparando notificação para: {}", agencyEmail);
                    emailService.sendCandidateApplicationNotification(agencyEmail, applicationDto);
                }
            });
        } else {
            emailService.sendCandidateApplicationNotification(agencyEmail, applicationDto);
        }
    }

    private void compensateUploadedFiles(String bucket, List<String> uploadedPaths) {
        for (String path : uploadedPaths) {
            try {
                log.info("Compensação: deletando arquivo órfão do bucket '{}', path='{}'", bucket, path);
                storageService.deleteFile(bucket, path);
            } catch (Exception ex) {
                log.warn("Falha na compensação de deleção do arquivo '{}' no bucket '{}': {}",
                        path, bucket, ex.getMessage());
            }
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        String name = filename.contains("/") ? filename.substring(filename.lastIndexOf('/') + 1) : filename;
        name = name.contains("\\") ? name.substring(name.lastIndexOf('\\') + 1) : name;
        String sanitized = name.trim().replaceAll("[^a-zA-Z0-9._-]", "-").replaceAll("-+", "-");
        return sanitized.isEmpty() ? "photo.jpg" : sanitized;
    }

    private record UploadedPhoto(int displayOrder, String storagePath, String publicUrl) {}
}
