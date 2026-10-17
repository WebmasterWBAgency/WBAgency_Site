package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import com.wbscouting.api.entity.CandidatePhoto;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.enums.MediaType;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.BusinessException;
import com.wbscouting.api.exception.DuplicatePromotionException;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.CandidatePhotoRepository;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.audit.AuditLogService;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateSubmissionAdminServiceImpl implements CandidateSubmissionAdminService {

    private final CandidateSubmissionRepository candidateSubmissionRepository;
    private final CandidatePhotoRepository candidatePhotoRepository;
    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateApplicationSummaryDto> listApplications(
            String status,
            String search,
            String gender,
            Boolean isMinor,
            Pageable pageable
    ) {
        log.info("Consulta administrativa tabular de candidaturas (candidate_submissions). Status: {}, Search: {}, isMinor: {}, Pageable: {}",
                status, search, isMinor, pageable);

        Page<CandidateSubmission> page;
        if (status != null && !status.isBlank()) {
            String upperStatus = status.trim().toUpperCase();
            if ("REJECTED".equals(upperStatus) || "DECLINED".equals(upperStatus)) {
                page = candidateSubmissionRepository.findByStatusInAndArchivedAtIsNull(
                    List.of("REJECTED", "DECLINED"),
                    pageable
                );
            } else if ("ARCHIVED".equals(upperStatus)) {
                List<CandidateSubmission> archivedSubs = candidateSubmissionRepository.findAllArchivedSubmissions();
                List<com.wbscouting.api.entity.Model> archivedModels = modelRepository.findAllArchivedModels();

                List<UUID> modelIds = archivedModels.stream().map(com.wbscouting.api.entity.Model::getId).toList();
                Map<UUID, List<com.wbscouting.api.entity.ModelMedia>> mediaByModel = Map.of();
                if (!modelIds.isEmpty()) {
                    List<com.wbscouting.api.entity.ModelMedia> allMedia = modelMediaRepository.findByModelIdInOrderByDisplayOrderAsc(modelIds);
                    mediaByModel = allMedia.stream()
                            .filter(m -> m.getModel() != null && m.getModel().getId() != null)
                            .collect(Collectors.groupingBy(m -> m.getModel().getId()));
                }
                final Map<UUID, List<com.wbscouting.api.entity.ModelMedia>> finalMediaMap = mediaByModel;

                List<CandidateApplicationSummaryDto> allArchivedDtos = new ArrayList<>();
                Set<String> seenProtocols = new HashSet<>();
                Set<UUID> seenIds = new HashSet<>();

                for (CandidateSubmission cs : archivedSubs) {
                    CandidateApplicationSummaryDto dto = CandidateApplicationSummaryDto.fromSubmission(cs, storageService);
                    if (dto != null) {
                        allArchivedDtos.add(dto);
                        if (dto.getId() != null) seenIds.add(dto.getId());
                        if (dto.getProtocol() != null && !dto.getProtocol().isBlank()) {
                            seenProtocols.add(dto.getProtocol().trim().toLowerCase());
                        }
                    }
                }

                for (com.wbscouting.api.entity.Model m : archivedModels) {
                    boolean idMatches = m.getId() != null && seenIds.contains(m.getId());
                    boolean protocolMatches = m.getProtocol() != null && !m.getProtocol().isBlank() && seenProtocols.contains(m.getProtocol().trim().toLowerCase());
                    if (!idMatches && !protocolMatches) {
                        CandidateApplicationSummaryDto dto = CandidateApplicationSummaryDto.fromModel(m, finalMediaMap.getOrDefault(m.getId(), List.of()));
                        if (dto != null) {
                            allArchivedDtos.add(dto);
                            if (m.getId() != null) seenIds.add(m.getId());
                            if (m.getProtocol() != null && !m.getProtocol().isBlank()) {
                                seenProtocols.add(m.getProtocol().trim().toLowerCase());
                            }
                        }
                    }
                }

                List<CandidateApplicationSummaryDto> filtered = allArchivedDtos.stream()
                        .filter(dto -> {
                            if (search != null && !search.isBlank()) {
                                String s = search.trim().toLowerCase();
                                boolean matchName = dto.getFullName() != null && dto.getFullName().toLowerCase().contains(s);
                                boolean matchEmail = dto.getEmail() != null && dto.getEmail().toLowerCase().contains(s);
                                boolean matchProtocol = dto.getProtocol() != null && dto.getProtocol().toLowerCase().contains(s);
                                boolean matchCity = dto.getCity() != null && dto.getCity().toLowerCase().contains(s);
                                if (!matchName && !matchEmail && !matchProtocol && !matchCity) {
                                    return false;
                                }
                            }
                            if (isMinor != null) {
                                if (dto.getIsMinor() != null && !dto.getIsMinor().equals(isMinor)) {
                                    return false;
                                }
                            }
                            return true;
                        })
                        .sorted((a, b) -> {
                            OffsetDateTime dateA = a.getArchivedAt() != null ? a.getArchivedAt() : a.getCreatedAt();
                            OffsetDateTime dateB = b.getArchivedAt() != null ? b.getArchivedAt() : b.getCreatedAt();
                            if (dateA == null && dateB == null) return 0;
                            if (dateA == null) return 1;
                            if (dateB == null) return -1;
                            return dateB.compareTo(dateA);
                        })
                        .collect(Collectors.toList());

                int totalElements = filtered.size();
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), totalElements);
                List<CandidateApplicationSummaryDto> paged = (start <= end && start < totalElements)
                        ? filtered.subList(start, end)
                        : List.of();

                return new PageImpl<>(paged, pageable, totalElements);
            } else {
                page = candidateSubmissionRepository.findByStatusAndArchivedAtIsNull(
                    upperStatus, 
                    pageable
                );
            }
        } else {
            page = candidateSubmissionRepository.findByArchivedAtIsNull(pageable);
        }

        List<CandidateApplicationSummaryDto> dtoList = page.getContent().stream()
                .filter(submission -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.trim().toLowerCase();
                        boolean matchName = submission.getFullName() != null && submission.getFullName().toLowerCase().contains(s);
                        boolean matchEmail = submission.getEmail() != null && submission.getEmail().toLowerCase().contains(s);
                        boolean matchProtocol = submission.getProtocol() != null && submission.getProtocol().toLowerCase().contains(s);
                        boolean matchCity = submission.getCity() != null && submission.getCity().toLowerCase().contains(s);
                        if (!matchName && !matchEmail && !matchProtocol && !matchCity) {
                            return false;
                        }
                    }
                    if (gender != null && !gender.isBlank()) {
                        if (submission.getGender() == null || !submission.getGender().equalsIgnoreCase(gender.trim())) {
                            return false;
                        }
                    }
                    if (isMinor != null) {
                        boolean minor = submission.getBirthDate() != null
                                && Period.between(submission.getBirthDate(), LocalDate.now()).getYears() < 18;
                        if (minor != isMinor) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(sub -> CandidateApplicationSummaryDto.fromSubmission(sub, storageService))
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCounts() {
        long pending = candidateSubmissionRepository.countByStatusAndArchivedAtIsNull("PENDING");
        long approved = candidateSubmissionRepository.countByStatusAndArchivedAtIsNull("APPROVED");
        long rejected = candidateSubmissionRepository.countByStatusInAndArchivedAtIsNull(List.of("REJECTED", "DECLINED"));

        List<CandidateSubmission> archivedSubs = candidateSubmissionRepository.findAllArchivedSubmissions();
        List<com.wbscouting.api.entity.Model> archivedModels = modelRepository.findAllArchivedModels();
        Set<String> seenProtocols = new HashSet<>();
        Set<UUID> seenIds = new HashSet<>();
        long archived = 0;

        for (CandidateSubmission cs : archivedSubs) {
            archived++;
            if (cs.getId() != null) seenIds.add(cs.getId());
            if (cs.getProtocol() != null && !cs.getProtocol().isBlank()) {
                seenProtocols.add(cs.getProtocol().trim().toLowerCase());
            }
        }

        for (com.wbscouting.api.entity.Model m : archivedModels) {
            boolean idMatches = m.getId() != null && seenIds.contains(m.getId());
            boolean protocolMatches = m.getProtocol() != null && !m.getProtocol().isBlank() && seenProtocols.contains(m.getProtocol().trim().toLowerCase());
            if (!idMatches && !protocolMatches) {
                archived++;
                if (m.getId() != null) seenIds.add(m.getId());
                if (m.getProtocol() != null && !m.getProtocol().isBlank()) {
                    seenProtocols.add(m.getProtocol().trim().toLowerCase());
                }
            }
        }

        long total = pending + approved + rejected;

        return Map.of(
                "pending", pending,
                "approved", approved,
                "rejected", rejected,
                "archived", archived,
                "total", total
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateDetailResponseDto getApplicationById(UUID id) {
        log.info("Buscando detalhes da candidatura ID: {}", id);
        CandidateSubmission submission = candidateSubmissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));
        return CandidateDetailResponseDto.fromSubmission(submission, storageService);
    }

    @Override
    @Transactional
    public CandidateDetailResponseDto updateDecision(UUID id, CandidateDecisionRequestDto decisionDto) {
        log.info("Decisão de triagem para candidatura ID: {}, Status: {}", id, decisionDto.getStatus());
        CandidateSubmission submission = candidateSubmissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura", "id", id));

        String currentStatus = submission.getStatus() != null ? submission.getStatus().trim().toUpperCase() : "PENDING";
        if ("PROMOTED".equals(currentStatus)) {
            throw new IllegalStateException("Candidaturas já promovidas não podem ter seu status alterado via triagem.");
        }

        if (decisionDto.getStatus() != null) {
            SubmissionStatus targetStatus = decisionDto.getStatus();
            submission.setStatus(targetStatus.name());
            if (targetStatus == SubmissionStatus.ARCHIVED) {
                submission.setArchivedAt(OffsetDateTime.now());
            }
        }
        if (decisionDto.getInternalNotes() != null) {
            submission.setFeedbackNotes(decisionDto.getInternalNotes());
        }
        submission.setReviewedAt(OffsetDateTime.now());

        CandidateSubmission saved = candidateSubmissionRepository.save(submission);
        return CandidateDetailResponseDto.fromSubmission(saved, storageService);
    }

    @Override
    @Transactional
    public CandidateDetailResponseDto restoreApplication(UUID id) {
        log.info("Restaurando candidatura do arquivo morto. ID: {}", id);
        CandidateSubmission submission = candidateSubmissionRepository.findById(id).orElse(null);
        if (submission != null) {
            submission.setArchivedAt(null);
            submission.setStatus("PENDING");
            submission.setReviewedAt(OffsetDateTime.now());
            submission.setUpdatedAt(OffsetDateTime.now());
            CandidateSubmission saved = candidateSubmissionRepository.save(submission);

            modelRepository.findById(id).ifPresent(m -> {
                m.setArchivedAt(null);
                m.setStatus(TalentLifecycleStatus.APPLICANT_PENDING);
                m.setIsActive(true);
                m.setUpdatedAt(OffsetDateTime.now());
                modelRepository.save(m);
            });

            return CandidateDetailResponseDto.fromSubmission(saved, storageService);
        }

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));
        model.setArchivedAt(null);
        model.setStatus(TalentLifecycleStatus.APPLICANT_PENDING);
        model.setIsActive(true);
        model.setUpdatedAt(OffsetDateTime.now());
        modelRepository.save(model);

        return CandidateDetailResponseDto.fromModel(model, List.of(), storageService);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID promoteToModel(UUID id, Boolean activateImmediately) {
        log.info("Iniciando promoção atômica da candidatura ID: {} para Modelo Oficial", id);

        CandidateSubmission submission = candidateSubmissionRepository.findById(id).orElse(null);
        if (submission == null) {
            Optional<Model> modelOpt = modelRepository.findById(id);
            if (modelOpt.isPresent()) {
                Model model = modelOpt.get();
                model.setStatus(TalentLifecycleStatus.CAST_PUBLIC);
                model.setIsActive(Boolean.TRUE.equals(activateImmediately));
                model.setUpdatedAt(OffsetDateTime.now());
                modelRepository.save(model);
                return model.getId();
            }
            throw new ResourceNotFoundException("Candidatura não encontrada com ID: " + id);
        }

        // Regra 1: A submissão precisa estar APPROVED
        if (!"APPROVED".equalsIgnoreCase(submission.getStatus())) {
            throw new IllegalStateException("Apenas candidaturas aprovadas podem ser promovidas para o casting. Status atual: " + submission.getStatus());
        }

        // Regra 2: Se promotedModelId já existir, lançar DuplicatePromotionException
        if (submission.getConvertedToModelId() != null) {
            throw new DuplicatePromotionException("Candidatura já foi promovida anteriormente ao modelo com ID: " + submission.getConvertedToModelId());
        }

        Model model = modelRepository.findById(id)
                .or(() -> submission.getProtocol() != null ? modelRepository.findByProtocol(submission.getProtocol()) : Optional.empty())
                .orElse(null);

        if (model == null) {
            String stageName = deriveStageName(submission.getFullName());
            GenderType gender = deriveGender(submission.getGender());
            model = Model.builder()
                    .stageName(stageName)
                    .fullName(submission.getFullName())
                    .email(submission.getEmail())
                    .phone(submission.getPhone())
                    .gender(gender)
                    .birthDate(submission.getBirthDate())
                    .age(submission.getAge())
                    .heightCm(submission.getHeightCm())
                    .bustCm(submission.getBustCm())
                    .waistCm(submission.getWaistCm())
                    .hipsCm(submission.getHipsCm())
                    .shoeSize(submission.getShoeSize() != null ? submission.getShoeSize().toString() : null)
                    .eyesColor(submission.getEyeColor())
                    .hairColor(submission.getHairColor())
                    .city(submission.getCity())
                    .state(submission.getState())
                    .instagramUrl(submission.getInstagramHandle())
                    .legalGuardianName(submission.getGuardianName())
                    .legalGuardianContact(submission.getGuardianPhone())
                    .protocol(submission.getProtocol())
                    .status(TalentLifecycleStatus.ACTIVE)
                    .isActive(Boolean.TRUE.equals(activateImmediately))
                    .isStar(false)
                    .isFeaturedHome(false)
                    .facePhotoUrl(submission.getFacePhotoUrl())
                    .profilePhotoUrl(submission.getProfilePhotoUrl())
                    .fullBodyPhotoUrl(submission.getFullBodyPhotoUrl())
                    .lgpdAccepted(submission.getLgpdConsent() != null ? submission.getLgpdConsent() : true)
                    .build();
        } else {
            model.setStatus(TalentLifecycleStatus.ACTIVE);
            model.setIsActive(Boolean.TRUE.equals(activateImmediately));
            model.setUpdatedAt(OffsetDateTime.now());
        }

        Model savedModel = modelRepository.save(model);

        List<String> copiedPaths = new ArrayList<>();
        try {
            List<CandidatePhoto> photos = candidatePhotoRepository.findBySubmissionIdOrderByDisplayOrderAsc(id);
            if (photos == null || photos.isEmpty()) {
                photos = submission.getPhotos();
            }

            boolean coverAssigned = false;
            String coverUrl = null;

            if (photos != null && !photos.isEmpty()) {
                for (int i = 0; i < photos.size(); i++) {
                    CandidatePhoto cp = photos.get(i);
                    String srcPath = cp.getStoragePath() != null ? cp.getStoragePath() : cp.getFilePath();
                    if (srcPath == null || srcPath.isBlank()) {
                        srcPath = cp.getFileUrl();
                    }

                    int order = cp.getDisplayOrder() != null ? cp.getDisplayOrder() : (i + 1);
                    boolean isCover = !coverAssigned && (i == 0 || order == 1 || (cp.getPhotoPosition() != null && cp.getPhotoPosition() == 1));
                    if (isCover) {
                        coverAssigned = true;
                    }

                    String filename = extractFilename(srcPath);
                    String destPath = String.format("%s/%s-%s", savedModel.getId(), UUID.randomUUID(), filename);

                    String copiedDest = destPath;
                    if (storageService != null && srcPath != null && !srcPath.isBlank()) {
                        try {
                            copiedDest = storageService.copyFile("candidates-uploads", srcPath, "models-media", destPath);
                            copiedPaths.add(copiedDest);
                        } catch (Exception copyEx) {
                            log.warn("Falha ao copiar binário no storage ({}); mantendo caminho relativo.", copyEx.getMessage());
                            copiedDest = destPath;
                        }
                    }

                    String publicUrl = (storageService != null) ? storageService.getPublicUrl("models-media", copiedDest) : copiedDest;
                    if (isCover && coverUrl == null) {
                        coverUrl = publicUrl;
                    }

                    ModelMedia media = ModelMedia.builder()
                            .model(savedModel)
                            .mediaType(MediaType.POLAROID)
                            .fileUrl(publicUrl != null ? publicUrl : copiedDest)
                            .filePath(copiedDest)
                            .displayOrder(order)
                            .isCover(isCover)
                            .isActive(true)
                            .build();

                    modelMediaRepository.save(media);
                }
            }

            if (coverUrl != null) {
                savedModel.setPrimaryPhotoUrl(coverUrl);
                savedModel.setFacePhotoUrl(coverUrl);
                modelRepository.save(savedModel);
            }

            submission.setConvertedToModelId(savedModel.getId());
            submission.setStatus("PROMOTED");
            submission.setReviewedAt(OffsetDateTime.now());
            candidateSubmissionRepository.save(submission);

            log.info("Candidatura ID: {} promovida com sucesso ao Modelo ID: {}", id, savedModel.getId());
            return savedModel.getId();
        } catch (Exception ex) {
            log.error("Falha na migração da candidatura ID: {}. Efetuando compensação de mídias...", id, ex);
            if (storageService != null) {
                for (String path : copiedPaths) {
                    try {
                        storageService.deleteFile("models-media", path);
                    } catch (Exception ignored) {}
                }
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteApplication(UUID id) {
        deleteApplication(id, true);
    }

    @Override
    @Transactional
    public void deleteApplication(UUID id, boolean permanent) {
        log.info("Processando exclusão da candidatura ID: {}, permanent: {}", id, permanent);
        CandidateSubmission submission = candidateSubmissionRepository.findById(id).orElse(null);
        if (submission == null) {
            throw new ResourceNotFoundException("Candidatura", "id", id);
        }

        String currentStatus = submission.getStatus() != null ? submission.getStatus().trim().toUpperCase() : "PENDING";

        // Trava de segurança mandatória (MEDIO-004):
        // Candidaturas com status PENDENTE ou APROVADO não podem ser excluídas fisicamente.
        if (submission.getCandidateStatus() == CandidateStatus.PENDING || submission.getCandidateStatus() == CandidateStatus.APPROVED
                || "PENDING".equals(currentStatus) || "APPROVED".equals(currentStatus)) {
            throw new IllegalStateException("Candidaturas com status PENDENTE ou APROVADO não podem ser excluídas fisicamente. Altere o status para REJEITADO antes de realizar a exclusão.");
        }

        if (!permanent) {
            submission.setStatus("ARCHIVED");
            submission.setArchivedAt(OffsetDateTime.now());
            submission.setUpdatedAt(OffsetDateTime.now());
            candidateSubmissionRepository.save(submission);
            log.info("Candidatura ID: {} arquivada com sucesso.", id);
        } else {
            // Hard Delete: autorizado SOMENTE para status REJECTED ou DECLINED (ou ARCHIVED)
            if (!"REJECTED".equals(currentStatus) && !"DECLINED".equals(currentStatus) && !"ARCHIVED".equals(currentStatus)) {
                throw new IllegalStateException("Candidaturas com status PENDENTE ou APROVADO não podem ser excluídas fisicamente. Altere o status para REJEITADO antes de realizar a exclusão.");
            }

            // 1. Obtenha todas as fotos filhas em CandidatePhoto associadas ao submissionId
            List<CandidatePhoto> photos = candidatePhotoRepository.findBySubmissionIdOrderByDisplayOrderAsc(id);
            if (photos == null || photos.isEmpty()) {
                photos = submission.getPhotos();
            }

            // 2. Para cada foto, execute a exclusão física do binário no Supabase Storage via StorageService no bucket candidates-uploads
            if (photos != null && storageService != null) {
                for (CandidatePhoto photo : photos) {
                    String path = photo.getStoragePath() != null ? photo.getStoragePath() : photo.getFilePath();
                    if (path != null && !path.isBlank()) {
                        try {
                            storageService.deleteFile("candidates-uploads", path);
                        } catch (Exception ex) {
                            log.warn("Falha ao excluir foto física no storage durante expurgo: {}", ex.getMessage());
                        }
                    }
                }
            }

            // 3. Exclua os registros de CandidatePhoto e CandidateSubmission
            if (photos != null && !photos.isEmpty()) {
                candidatePhotoRepository.deleteAll(photos);
            }
            candidateSubmissionRepository.delete(submission);

            // 4. Registre a operação no AuditLogService documentando a remoção definitiva autorizada em admin_audit_logs
            if (auditLogService != null) {
                auditLogService.recordLogAsync(
                        null,
                        null,
                        "PURGE_CANDIDATE_DATA",
                        "CANDIDATE_SUBMISSION",
                        id.toString(),
                        "Remoção definitiva autorizada e expurgo físico de candidatura e fotos associadas sob conformidade LGPD.",
                        Map.of("protocol", submission.getProtocol() != null ? submission.getProtocol() : id.toString(), "action", "PERMANENT_REMOVAL_AUTHORIZED"),
                        null,
                        null
                );
            }

            log.info("Expurgo atômico concluído com sucesso para candidatura ID: {}", id);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> purgeArchive(boolean all) {
        log.info("[ARCHIVE-PURGE] Iniciando expurgo do arquivo morto (all={})", all);
        List<CandidateSubmission> archivedSubmissions = candidateSubmissionRepository.findAllArchivedSubmissions();
        int purgedCount = 0;

        for (CandidateSubmission sub : archivedSubmissions) {
            UUID subId = sub.getId();
            List<CandidatePhoto> photos = candidatePhotoRepository.findBySubmissionIdOrderByDisplayOrderAsc(subId);
            if (photos != null && storageService != null) {
                for (CandidatePhoto photo : photos) {
                    String path = photo.getStoragePath() != null ? photo.getStoragePath() : photo.getFilePath();
                    if (path != null && !path.isBlank()) {
                        try {
                            storageService.deleteFile("candidates-uploads", path);
                        } catch (Exception ignored) {}
                    }
                }
                candidatePhotoRepository.deleteAll(photos);
            }
            candidateSubmissionRepository.delete(sub);
            purgedCount++;

            if (auditLogService != null) {
                auditLogService.recordLogAsync(
                        null,
                        null,
                        "PURGE_CANDIDATE_DATA",
                        "CANDIDATE_SUBMISSION",
                        subId.toString(),
                        "Expurgo em lote do arquivo morto sob conformidade LGPD.",
                        Map.of("protocol", sub.getProtocol() != null ? sub.getProtocol() : subId.toString()),
                        null,
                        null
                );
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("purgedCount", purgedCount);
        response.put("message", "Expurgo do arquivo morto concluído com sucesso! " + purgedCount + " registro(s) expurgado(s).");
        return response;
    }

    private String extractFilename(String path) {
        if (path == null || path.isBlank()) return "photo.jpg";
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (idx >= 0 && idx < path.length() - 1) {
            return path.substring(idx + 1);
        }
        return path;
    }

    private String deriveStageName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Novo Modelo";
        String trimmed = fullName.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length > 1) {
            return parts[0] + " " + parts[parts.length - 1];
        }
        return trimmed;
    }

    private GenderType deriveGender(String gender) {
        if (gender == null || gender.isBlank()) return GenderType.FEMALE;
        String g = gender.trim().toUpperCase();
        if (g.startsWith("M") || g.contains("MASC")) {
            return GenderType.MALE;
        }
        return GenderType.FEMALE;
    }
}
