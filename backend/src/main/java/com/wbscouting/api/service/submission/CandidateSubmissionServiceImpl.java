package com.wbscouting.api.service.submission;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.CandidateSubmissionRequestDto;
import com.wbscouting.api.dto.CandidateSubmissionResponseDto;
import com.wbscouting.api.entity.Candidate;
import com.wbscouting.api.entity.CandidatePhoto;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.exception.BusinessException;
import com.wbscouting.api.repository.CandidatePhotoRepository;
import com.wbscouting.api.repository.CandidateRepository;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.service.storage.StorageService;
import com.wbscouting.api.service.storage.SupabaseStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CandidateSubmissionServiceImpl implements CandidateSubmissionService {

    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");

    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final CandidateSubmissionRepository repository;
    private final CandidatePhotoRepository candidatePhotoRepository;
    private final StorageService storageService;
    private final SupabaseProperties supabaseProperties;
    private final ProtocolGeneratorService protocolGeneratorService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.wbscouting.api.repository.CandidateRepository candidateRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.wbscouting.api.repository.ModelRepository modelRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.wbscouting.api.repository.ModelMediaRepository modelMediaRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public CandidateSubmissionServiceImpl(CandidateSubmissionRepository repository,
                                          CandidatePhotoRepository candidatePhotoRepository,
                                          StorageService storageService,
                                          SupabaseProperties supabaseProperties,
                                          ProtocolGeneratorService protocolGeneratorService) {
        this.repository = repository;
        this.candidatePhotoRepository = candidatePhotoRepository;
        this.storageService = storageService;
        this.supabaseProperties = supabaseProperties;
        this.protocolGeneratorService = protocolGeneratorService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CandidateSubmissionResponseDto submit(
            CandidateSubmissionRequestDto request,
            MultipartFile facePhoto,
            MultipartFile profilePhoto,
            MultipartFile fullBodyPhoto
    ) {
        return submit(request, facePhoto, profilePhoto, fullBodyPhoto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CandidateSubmissionResponseDto submit(
            CandidateSubmissionRequestDto request,
            MultipartFile facePhoto,
            MultipartFile profilePhoto,
            MultipartFile fullBodyPhoto,
            List<MultipartFile> additionalPhotos
    ) {
        log.info("Processando submissão pública de candidatura para: {}", request.getFullName());

        // 1. Validação de Idade e Responsável Legal
        int age = calculateAndValidateAge(request.getBirthDate());
        if (age < 18) {
            validateGuardianInfo(request);
        }

        // 2. Validação Estrita dos Arquivos de Foto
        validateImageFile(facePhoto, "Rosto (facePhoto)");
        validateImageFile(profilePhoto, "Perfil (profilePhoto)");
        validateImageFile(fullBodyPhoto, "Corpo Inteiro (fullBodyPhoto)");

        if (additionalPhotos != null && !additionalPhotos.isEmpty()) {
            int addIdx = 4;
            for (MultipartFile photo : additionalPhotos) {
                if (photo != null && !photo.isEmpty()) {
                    validateImageFile(photo, "Foto Adicional #" + (addIdx++));
                }
            }
        }

        // 3. Sanitização de Entradas de Texto
        String sanitizedFullName = sanitizeString(request.getFullName());
        String sanitizedCity = sanitizeString(request.getCity());
        String sanitizedState = request.getState().trim().toUpperCase();
        String sanitizedInstagram = sanitizeInstagram(request.getInstagramHandle());
        String sanitizedGuardianName = sanitizeString(request.getGuardianName());
        String sanitizedEyeColor = sanitizeString(request.getEyeColor());
        String sanitizedHairColor = sanitizeString(request.getHairColor());

        // 4. Parâmetros e rastreabilidade de uploads no service (Subetapa 2.3)
        final String bucket = supabaseProperties.resolveBucketCandidates();
        final UUID storageId = UUID.randomUUID();
        final List<String> uploadedStorageKeys = new ArrayList<>();

        // Geração de protocolo único
        String protocol = protocolGeneratorService.generateUniqueProtocol();

        // 5. Upload das 3 fotos obrigatórias para o Storage Supabase e coleta das chaves
        String facePath = String.format("submissions/%s/face_%s", storageId, cleanFileName(facePhoto.getOriginalFilename()));
        String profilePath = String.format("submissions/%s/profile_%s", storageId, cleanFileName(profilePhoto.getOriginalFilename()));
        String fullBodyPath = String.format("submissions/%s/fullbody_%s", storageId, cleanFileName(fullBodyPhoto.getOriginalFilename()));

        storageService.uploadFile(bucket, facePath, facePhoto);
        uploadedStorageKeys.add(facePath);

        storageService.uploadFile(bucket, profilePath, profilePhoto);
        uploadedStorageKeys.add(profilePath);

        storageService.uploadFile(bucket, fullBodyPath, fullBodyPhoto);
        uploadedStorageKeys.add(fullBodyPath);

        String faceUrl = storageService.getPublicUrl(bucket, facePath);
        String profileUrl = storageService.getPublicUrl(bucket, profilePath);
        String fullBodyUrl = storageService.getPublicUrl(bucket, fullBodyPath);

        // Upload das fotos adicionais (posições 4 em diante)
        List<String> additionalPaths = new ArrayList<>();
        List<String> additionalUrls = new ArrayList<>();
        if (additionalPhotos != null && !additionalPhotos.isEmpty()) {
            int pos = 4;
            for (MultipartFile addPhoto : additionalPhotos) {
                if (addPhoto != null && !addPhoto.isEmpty()) {
                    String addPath = String.format("submissions/%s/photo_%d_%s", storageId, pos++, cleanFileName(addPhoto.getOriginalFilename()));
                    storageService.uploadFile(bucket, addPath, addPhoto);
                    uploadedStorageKeys.add(addPath);
                    additionalPaths.add(addPath);
                    additionalUrls.add(storageService.getPublicUrl(bucket, addPath));
                }
            }
        }

        // 6. Bloco de persistência relacional com transação compensatória (Subetapa 2.3)
        try {
            CandidateSubmission submission = CandidateSubmission.builder()
                    .protocol(protocol)
                    .fullName(sanitizedFullName)
                    .email(request.getEmail().trim().toLowerCase())
                    .phone(request.getPhone().trim())
                    .birthDate(request.getBirthDate())
                    .age(age)
                    .gender(request.getGender())
                    .city(sanitizedCity)
                    .state(sanitizedState)
                    .height(request.getHeight())
                    .bust(request.getBust())
                    .waist(request.getWaist())
                    .hips(request.getHips())
                    .shoeSize(request.getShoeSize())
                    .eyeColor(sanitizedEyeColor)
                    .hairColor(sanitizedHairColor)
                    .instagramHandle(sanitizedInstagram)
                    .guardianName(sanitizedGuardianName)
                    .guardianPhone(StringUtils.hasText(request.getGuardianPhone()) ? request.getGuardianPhone().trim() : null)
                    .guardianEmail(StringUtils.hasText(request.getGuardianEmail()) ? request.getGuardianEmail().trim().toLowerCase() : null)
                    .lgpdConsent(Boolean.TRUE.equals(request.getLgpdConsent()))
                    .lgpdConsentAt(OffsetDateTime.now())
                    .status(SubmissionStatus.PENDING)
                    .facePhotoUrl(faceUrl)
                    .profilePhotoUrl(profileUrl)
                    .fullBodyPhotoUrl(fullBodyUrl)
                    .build();

            CandidateSubmission saved = repository.saveAndFlush(submission);
            log.info("[SUBMIT] Candidatura gravada com sucesso em 'candidate_submissions' — ID={} | Protocolo={}",
                    saved.getId(), saved.getProtocol());

            // Espelha no repositório canônico Candidate para manter integridade com candidate_photos(candidate_id)
            if (candidateRepository != null) {
                try {
                    com.wbscouting.api.entity.Candidate canonicalCandidate = com.wbscouting.api.entity.Candidate.builder()
                            .id(saved.getId())
                            .protocol(saved.getProtocol())
                            .fullName(saved.getFullName())
                            .email(saved.getEmail())
                            .phone(saved.getPhone())
                            .birthDate(saved.getBirthDate())
                            .age(saved.getAge())
                            .gender(saved.getGender() != null ? saved.getGender().name() : null)
                            .city(saved.getCity())
                            .state(saved.getState())
                            .heightCm(saved.getHeight() != null ? saved.getHeight().multiply(BigDecimal.valueOf(100)) : null)
                            .bustChestCm(saved.getBust())
                            .waistCm(saved.getWaist())
                            .hipsCm(saved.getHips())
                            .shoeSize(saved.getShoeSize() != null ? String.valueOf(saved.getShoeSize()) : null)
                            .instagramHandle(saved.getInstagramHandle())
                            .legalGuardianName(saved.getGuardianName())
                            .legalGuardianContact(saved.getGuardianPhone())
                            .lgpdAccepted(Boolean.TRUE.equals(saved.getLgpdConsent()))
                            .status(com.wbscouting.api.enums.CandidateStatus.PENDING)
                            .build();
                    candidateRepository.saveAndFlush(canonicalCandidate);
                    log.info("[SUBMIT] Candidato canônico espelhado com sucesso — ID={}", saved.getId());
                } catch (Exception ex) {
                    log.warn("[SUBMIT] Aviso ao espelhar em candidates canônica: {}", ex.getMessage());
                }
            }

            // Persistência direta no modelo canônico Model (Single-Table Lifecycle)
            if (modelRepository != null) {
                try {
                    com.wbscouting.api.enums.GenderType modelGender = (saved.getGender() != null && saved.getGender().name().equalsIgnoreCase("MALE"))
                            ? com.wbscouting.api.enums.GenderType.MALE
                            : com.wbscouting.api.enums.GenderType.FEMALE;

                    Integer hCm = null;
                    if (saved.getHeight() != null) {
                        hCm = saved.getHeight().compareTo(BigDecimal.valueOf(3)) < 0
                                ? saved.getHeight().multiply(BigDecimal.valueOf(100)).intValue()
                                : saved.getHeight().intValue();
                    }

                    com.wbscouting.api.entity.Model canonicalModel = com.wbscouting.api.entity.Model.builder()
                            .id(saved.getId())
                            .protocol(saved.getProtocol())
                            .stageName(saved.getFullName())
                            .fullName(saved.getFullName())
                            .email(saved.getEmail())
                            .phone(saved.getPhone())
                            .birthDate(saved.getBirthDate())
                            .age(saved.getAge())
                            .gender(modelGender)
                            .city(saved.getCity())
                            .state(saved.getState())
                            .heightCm(hCm)
                            .bustChestCm(saved.getBust())
                            .waistCm(saved.getWaist())
                            .hipsCm(saved.getHips())
                            .shoeSize(saved.getShoeSize() != null ? String.valueOf(saved.getShoeSize()) : null)
                            .hairColor(saved.getHairColor())
                            .eyesColor(saved.getEyeColor())
                            .instagramUrl(saved.getInstagramHandle())
                            .legalGuardianName(saved.getGuardianName())
                            .legalGuardianContact(saved.getGuardianPhone())
                            .lgpdAccepted(Boolean.TRUE.equals(saved.getLgpdConsent()))
                            .status(com.wbscouting.api.enums.TalentLifecycleStatus.APPLICANT_PENDING)
                            .isActive(true)
                            .isStar(false)
                            .primaryPhotoUrl(faceUrl)
                            .build();

                    modelRepository.saveAndFlush(canonicalModel);
                    log.info("[SUBMIT][SINGLE-TABLE] Model canônico criado com sucesso com status APPLICANT_PENDING — ID={}", canonicalModel.getId());

                    // Persiste mídias filhas em model_media
                    if (modelMediaRepository != null) {
                        List<com.wbscouting.api.entity.ModelMedia> mediaList = new ArrayList<>();
                        int order = 1;

                        // Foto 1 (face): marcada como capa oficial
                        mediaList.add(com.wbscouting.api.entity.ModelMedia.builder()
                                .model(canonicalModel)
                                .mediaType(com.wbscouting.api.enums.MediaType.BOOK)
                                .fileUrl(faceUrl)
                                .filePath(facePath)
                                .displayOrder(order++)
                                .isCover(true)
                                .isActive(true)
                                .build());

                        // Foto 2 (profile)
                        mediaList.add(com.wbscouting.api.entity.ModelMedia.builder()
                                .model(canonicalModel)
                                .mediaType(com.wbscouting.api.enums.MediaType.BOOK)
                                .fileUrl(profileUrl)
                                .filePath(profilePath)
                                .displayOrder(order++)
                                .isCover(false)
                                .isActive(true)
                                .build());

                        // Foto 3 (full body)
                        mediaList.add(com.wbscouting.api.entity.ModelMedia.builder()
                                .model(canonicalModel)
                                .mediaType(com.wbscouting.api.enums.MediaType.BOOK)
                                .fileUrl(fullBodyUrl)
                                .filePath(fullBodyPath)
                                .displayOrder(order++)
                                .isCover(false)
                                .isActive(true)
                                .build());

                        // Fotos adicionais (se houver)
                        for (int i = 0; i < additionalPaths.size(); i++) {
                            mediaList.add(com.wbscouting.api.entity.ModelMedia.builder()
                                    .model(canonicalModel)
                                    .mediaType(com.wbscouting.api.enums.MediaType.BOOK)
                                    .fileUrl(additionalUrls.get(i))
                                    .filePath(additionalPaths.get(i))
                                    .displayOrder(order++)
                                    .isCover(false)
                                    .isActive(true)
                                    .build());
                        }

                        modelMediaRepository.saveAll(mediaList);
                        canonicalModel.setMediaList(mediaList);
                        log.info("[SUBMIT][SINGLE-TABLE] {} mídias associadas à model_media(modelId={}) com foto 1 como is_cover=true", mediaList.size(), canonicalModel.getId());
                    }
                } catch (Exception ex) {
                    log.warn("[SUBMIT][SINGLE-TABLE] Aviso ao gravar em models/model_media canônica: {}", ex.getMessage(), ex);
                }
            }

            // 7. Associação de TODAS as fotos filhas na tabela candidate_photos
            List<CandidatePhoto> photos = new ArrayList<>();
            short position = 1;
            photos.add(criarFotoFilha(saved, facePath, faceUrl, position++));
            photos.add(criarFotoFilha(saved, profilePath, profileUrl, position++));
            photos.add(criarFotoFilha(saved, fullBodyPath, fullBodyUrl, position++));

            for (int i = 0; i < additionalPaths.size(); i++) {
                photos.add(criarFotoFilha(saved, additionalPaths.get(i), additionalUrls.get(i), position++));
            }

            candidatePhotoRepository.saveAll(photos);
            saved.setPhotos(photos);
            log.info("[SUBMIT][CANDIDATE_PHOTOS] {} fotos associadas a candidate_submissions/candidates(id={})", photos.size(), saved.getId());

            return CandidateSubmissionResponseDto.builder()
                    .id(saved.getId())
                    .protocol(saved.getProtocol())
                    .message("Candidatura enviada com sucesso! Nossa equipe de scouting analisará seu material.")
                    .status(saved.getStatus())
                    .fullName(saved.getFullName())
                    .email(saved.getEmail())
                    .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : OffsetDateTime.now())
                    .build();

        } catch (DataAccessException ex) {
            log.error("FALHA NA SUBMISSÃO: Purgando {} arquivos órfãos do storage.", uploadedStorageKeys.size());
            purgeUploadedStorageKeys(bucket, uploadedStorageKeys);
            throw new BusinessException("Falha ao salvar candidatura no banco de dados. Transação revertida.", ex);
        } catch (RuntimeException ex) {
            log.error("FALHA NA SUBMISSÃO: Purgando {} arquivos órfãos do storage.", uploadedStorageKeys.size());
            purgeUploadedStorageKeys(bucket, uploadedStorageKeys);
            if (ex instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException("Falha ao salvar candidatura no banco de dados. Transação revertida.", ex);
        }
    }

    private void purgeUploadedStorageKeys(String bucket, List<String> uploadedStorageKeys) {
        if (uploadedStorageKeys == null || uploadedStorageKeys.isEmpty()) {
            return;
        }
        for (String key : uploadedStorageKeys) {
            try {
                storageService.deleteFile(bucket, key);
                log.info("[SUBMIT][PURGE] Arquivo órfão excluído do storage: {}/{}", bucket, key);
            } catch (Exception purgeEx) {
                log.warn("[SUBMIT][PURGE] Falha ao excluir arquivo órfão {} do bucket {}: {}",
                        key, bucket, purgeEx.getMessage());
            }
        }
    }

    private int calculateAndValidateAge(LocalDate birthDate) {
        if (birthDate == null) {
            throw new BusinessException("Data de nascimento é obrigatória.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new BusinessException("A data de nascimento deve ser uma data passada.");
        }
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        if (age < 2 || age > 99) {
            throw new BusinessException("Idade fora dos limites operacionais de agenciamento.");
        }
        return age;
    }

    private void validateGuardianInfo(CandidateSubmissionRequestDto request) {
        if (!StringUtils.hasText(request.getGuardianName())) {
            throw new BusinessException("Para candidatos menores de 18 anos, o nome do responsável legal é obrigatório.");
        }
        if (!StringUtils.hasText(request.getGuardianPhone())) {
            throw new BusinessException("Para candidatos menores de 18 anos, o telefone de contato do responsável legal é obrigatório.");
        }
        if (!StringUtils.hasText(request.getGuardianEmail())) {
            throw new BusinessException("Para candidatos menores de 18 anos, o e-mail do responsável legal é obrigatório.");
        }
        if (!EMAIL_PATTERN.matcher(request.getGuardianEmail().trim()).matches()) {
            throw new BusinessException("O e-mail do responsável legal possui formato inválido.");
        }
    }

    public static final long MAX_PHOTO_FILE_SIZE_BYTES = 8L * 1024 * 1024; // 8 MB
    public static final long MIN_UHD_PIXELS = 3_800_000L; // Mínimo ~4 Megapixels UHD (2560x1600 px ou superior)

    private static final byte[] WEBP_RIFF_MAGIC = new byte[]{'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_FORMAT_SIG = new byte[]{'W', 'E', 'B', 'P'};

    private void validateImageFile(MultipartFile file, String photoRole) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("O arquivo de foto " + photoRole + " é obrigatório e não pode estar vazio.");
        }

        if (file.getSize() > MAX_PHOTO_FILE_SIZE_BYTES) {
            throw new BusinessException(String.format(
                    "O arquivo enviado excede o limite máximo permitido de 8 MB para %s.", photoRole));
        }

        // Validação de integridade do cabeçalho (Magic Bytes): JPEG, PNG e WEBP
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new BusinessException("Arquivo corrompido ou inválido para " + photoRole + ".");
            }

            boolean isJpeg = header[0] == JPEG_MAGIC[0] && header[1] == JPEG_MAGIC[1] && header[2] == JPEG_MAGIC[2];
            boolean isPng = read >= 8 && Arrays.equals(java.util.Arrays.copyOf(header, 8), PNG_MAGIC);
            boolean isWebp = read >= 12
                    && header[0] == WEBP_RIFF_MAGIC[0] && header[1] == WEBP_RIFF_MAGIC[1]
                    && header[2] == WEBP_RIFF_MAGIC[2] && header[3] == WEBP_RIFF_MAGIC[3]
                    && header[8] == WEBP_FORMAT_SIG[0] && header[9] == WEBP_FORMAT_SIG[1]
                    && header[10] == WEBP_FORMAT_SIG[2] && header[11] == WEBP_FORMAT_SIG[3];

            if (!isJpeg && !isPng && !isWebp) {
                log.warn("Tentativa de upload com cabeçalho adulterado para {}. Bytes recebidos: {}", photoRole, Arrays.toString(header));
                throw new BusinessException("Cabeçalho de arquivo adulterado ou inválido para " + photoRole + ". Envie uma imagem válida (JPEG, PNG ou WEBP).");
            }
        } catch (IOException e) {
            log.error("Erro ao ler cabeçalho do arquivo de foto {}", photoRole, e);
            throw new BusinessException("Falha ao processar arquivo de imagem para " + photoRole + ".");
        }

        // Validação de Definição Mínima (4 Megapixels UHD)
        validateImageResolution(file, photoRole);
    }

    private void validateImageResolution(MultipartFile file, String photoRole) {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image != null) {
                long totalPixels = (long) image.getWidth() * image.getHeight();
                if (totalPixels < MIN_UHD_PIXELS) {
                    throw new BusinessException(String.format(
                            "A imagem enviada para %s possui baixa definição (%dx%d px - %.1f MP). Para garantir visualização nítida em telas de alta definição (UHD), envie fotos com resolução mínima de 4 Megapixels (aproximadamente 2560x1600 px ou superior).",
                            photoRole, image.getWidth(), image.getHeight(), totalPixels / 1_000_000.0));
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Não foi possível inspecionar dimensões da imagem para {}: {}", photoRole, e.getMessage());
        }
    }

    private String sanitizeString(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        // Remove tags HTML e espaços sobressalentes
        return HTML_PATTERN.matcher(value).replaceAll("").trim();
    }

    private String sanitizeInstagram(String handle) {
        if (!StringUtils.hasText(handle)) {
            return null;
        }
        String cleaned = HTML_PATTERN.matcher(handle).replaceAll("").trim();
        if (cleaned.startsWith("@")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned.toLowerCase();
    }

    private String cleanFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "photo.jpg";
        }
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }



    /**
     * Instancia e associa uma foto filha vinculada ao CandidateSubmission persistido.
     */
    private CandidatePhoto criarFotoFilha(CandidateSubmission submission, String storagePath, String publicUrl, short position) {
        CandidatePhoto photo = CandidatePhoto.builder()
                .submission(submission)
                .photoPosition(position)
                .fileUrl(publicUrl)
                .filePath(storagePath)
                .storagePath(storagePath)
                .displayOrder((int) position)
                .uploadedAt(OffsetDateTime.now())
                .build();
        if (submission != null && submission.getId() != null) {
            photo.setCandidateId(submission.getId());
        }
        return photo;
    }
}
