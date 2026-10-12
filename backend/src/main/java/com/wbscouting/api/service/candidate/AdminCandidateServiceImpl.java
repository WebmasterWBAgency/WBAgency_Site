package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.admin.candidate.*;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCandidateServiceImpl implements AdminCandidateService {

    public static final int SIGNED_URL_EXPIRES_IN_SECONDS = 900;

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final StorageService storageService;
    private final SupabaseProperties supabaseProperties;
    private final CandidateSubmissionRepository candidateSubmissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateListItemAdminDto> listCandidates(
            CandidateStatus status,
            String search,
            String gender,
            Boolean isMinor,
            Pageable pageable) {

        log.info("Listando candidaturas com filtros administrativos sobre Model: status={}, search='{}', gender={}, isMinor={}, pageable={}",
                status, search, gender, isMinor, pageable);

        List<TalentLifecycleStatus> statuses = mapCandidateStatusToLifecycle(status);

        Page<Model> modelPage = modelRepository.findAdminWithFiltersAndStatuses(
                null, null, null, statuses, (search != null && !search.isBlank()) ? search.trim() : null, pageable
        );

        if (modelPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<CandidateListItemAdminDto> dtoList = modelPage.getContent().stream()
                .filter(m -> {
                    if (isMinor != null) {
                        int age = calculateAge(m.getBirthDate(), m.getAge());
                        boolean minor = age < 18;
                        if (minor != isMinor) return false;
                    }
                    if (StringUtils.hasText(gender)) {
                        String g = m.getGender() != null ? m.getGender().name() : "";
                        if (!g.equalsIgnoreCase(gender.trim())) return false;
                    }
                    return true;
                })
                .map(this::toListItemDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, modelPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateDetailAdminDto getCandidateDetail(UUID id) {
        log.info("Buscando detalhes da candidatura ID: {}", id);

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        return toDetailDtoWithSignedUrls(model);
    }

    @Override
    @Transactional
    public CandidateDetailAdminDto updateStatus(UUID id, CandidateStatusUpdateDto dto) {
        log.info("Atualizando status da candidatura ID: {} para {}", id, dto.getStatus());

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        if (dto.getStatus() == CandidateStatus.APPROVED) {
            model.setStatus(TalentLifecycleStatus.APPLICANT_APPROVED);
        } else if (dto.getStatus() == CandidateStatus.REJECTED) {
            model.setStatus(TalentLifecycleStatus.APPLICANT_DECLINED);
        } else if (dto.getStatus() == CandidateStatus.ARCHIVED) {
            model.setStatus(TalentLifecycleStatus.ARCHIVED);
        } else if (dto.getStatus() == CandidateStatus.PENDING) {
            model.setStatus(TalentLifecycleStatus.APPLICANT_PENDING);
        }

        if (dto.getInternalNotes() != null && !dto.getInternalNotes().isBlank()) {
            model.setInternalNotes(dto.getInternalNotes());
        } else if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            model.setInternalNotes(dto.getNotes());
        } else if (dto.getAdminNotes() != null && !dto.getAdminNotes().isBlank()) {
            model.setInternalNotes(dto.getAdminNotes());
        }

        model.setUpdatedAt(OffsetDateTime.now());
        Model saved = modelRepository.save(model);
        return toDetailDtoWithSignedUrls(saved);
    }

    @Override
    @Transactional
    public CandidateDetailAdminDto updateNotes(UUID id, CandidateNotesUpdateDto dto) {
        log.info("Atualizando anotações internas da candidatura ID: {}", id);

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        model.setInternalNotes(dto.getInternalNotes());
        model.setUpdatedAt(OffsetDateTime.now());
        Model saved = modelRepository.save(model);
        return toDetailDtoWithSignedUrls(saved);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void deleteCandidate(UUID id) {
        deleteCandidate(id, false);
    }

    @Override
    @Transactional
    public void deleteCandidate(UUID id, boolean permanent) {
        log.info("Processando exclusão da candidatura ID: {}, permanent: {}", id, permanent);

        Model model = modelRepository.findById(id).orElse(null);
        CandidateSubmission sub = (candidateSubmissionRepository != null)
                ? candidateSubmissionRepository.findById(id).orElse(null)
                : null;
        if (sub == null && model != null && model.getProtocol() != null && candidateSubmissionRepository != null) {
            sub = candidateSubmissionRepository.findByProtocol(model.getProtocol()).orElse(null);
        }
        if (model == null && sub != null && sub.getProtocol() != null) {
            model = modelRepository.findByProtocol(sub.getProtocol()).orElse(null);
        }

        if (model == null && sub == null) {
            throw new ResourceNotFoundException("Candidatura não encontrada com ID: " + id);
        }

        if (!permanent) {
            OffsetDateTime now = OffsetDateTime.now();
            if (model != null) {
                if (model.getStatus() == TalentLifecycleStatus.CAST_PUBLIC || model.getStatus() == TalentLifecycleStatus.CAST_STAR) {
                    throw new IllegalStateException("Candidatos promovidos ao catálogo oficial não podem ser excluídos pelo Scouting Desk. Gerencie-os em Gestão de Modelos.");
                }
                model.setStatus(TalentLifecycleStatus.ARCHIVED);
                model.setIsActive(false);
                model.setArchivedAt(now);
                model.setUpdatedAt(now);
                modelRepository.save(model);
            }

            if (sub != null) {
                sub.setStatus("ARCHIVED");
                sub.setArchivedAt(now);
                sub.setUpdatedAt(now);
                candidateSubmissionRepository.save(sub);
            }

            if (jdbcTemplate != null) {
                try {
                    String p = model != null ? model.getProtocol() : (sub != null ? sub.getProtocol() : null);
                    jdbcTemplate.update(
                            "UPDATE public.candidate_submissions SET archived_at = NOW(), status = 'ARCHIVED', updated_at = NOW() WHERE id = ? OR (protocol IS NOT NULL AND protocol = ?)",
                            id, p
                    );
                } catch (Exception ex) {
                    log.debug("Soft delete em candidate_submissions fallback: {}", ex.getMessage());
                }
            }
            log.info("Candidatura ID: {} movida para Arquivo Morto com sucesso (archivedAt={}).", id, now);
        } else {
            // Hard delete: Expurgo físico de storage e remoção relacional
            String bucket = (supabaseProperties != null) ? supabaseProperties.resolveBucketCandidates() : "candidates-uploads";
            if (model != null) {
                List<ModelMedia> mediaList = modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(id);
                for (ModelMedia media : mediaList) {
                    String path = media.getFilePath() != null ? media.getFilePath() : media.getFileUrl();
                    if (path != null && !path.isBlank()) {
                        try {
                            storageService.deleteFile("candidates-uploads", path);
                        } catch (Exception ex) {
                            try {
                                storageService.deleteFile(bucket, path);
                            } catch (Exception ignored) {}
                        }
                    }
                }
                modelMediaRepository.deleteAll(mediaList);
                modelRepository.delete(model);
            }

            if (sub != null) {
                candidateSubmissionRepository.delete(sub);
            }

            if (jdbcTemplate != null) {
                try {
                    List<String> photoPaths = jdbcTemplate.query(
                            "SELECT COALESCE(file_path, storage_path) AS file_path FROM public.candidate_photos WHERE candidate_id = ?",
                            (rs, rowNum) -> rs.getString("file_path"),
                            id
                    );
                    for (String p : photoPaths) {
                        if (p != null && !p.isBlank()) {
                            try {
                                storageService.deleteFile("candidates-uploads", p);
                            } catch (Exception ignored) {}
                        }
                    }
                    jdbcTemplate.update("DELETE FROM public.candidate_photos WHERE candidate_id = ?", id);
                    String p = model != null ? model.getProtocol() : (sub != null ? sub.getProtocol() : null);
                    jdbcTemplate.update("DELETE FROM public.candidate_submissions WHERE id = ? OR (protocol IS NOT NULL AND protocol = ?)", id, p);
                } catch (Exception ex) {
                    log.debug("Expurgo relacional em candidate_submissions / photos: {}", ex.getMessage());
                }
            }

            log.info("Candidatura ID: {} e todas as suas mídias foram excluídas definitivamente.", id);
        }
    }

    @Override
    @Transactional
    public void restoreCandidate(UUID id) {
        log.info("Restaurando candidatura do arquivo morto ID: {}", id);
        Model model = modelRepository.findById(id).orElse(null);
        CandidateSubmission sub = (candidateSubmissionRepository != null)
                ? candidateSubmissionRepository.findById(id).orElse(null)
                : null;
        if (sub == null && model != null && model.getProtocol() != null && candidateSubmissionRepository != null) {
            sub = candidateSubmissionRepository.findByProtocol(model.getProtocol()).orElse(null);
        }
        if (model == null && sub != null && sub.getProtocol() != null) {
            model = modelRepository.findByProtocol(sub.getProtocol()).orElse(null);
        }

        if (model != null) {
            model.setStatus(TalentLifecycleStatus.APPLICANT_PENDING);
            model.setIsActive(true);
            model.setArchivedAt(null);
            model.setUpdatedAt(OffsetDateTime.now());
            modelRepository.save(model);
        }

        if (sub != null) {
            sub.setStatus("PENDING");
            sub.setArchivedAt(null);
            sub.setUpdatedAt(OffsetDateTime.now());
            candidateSubmissionRepository.save(sub);
        }

        if (jdbcTemplate != null) {
            try {
                String p = model != null ? model.getProtocol() : (sub != null ? sub.getProtocol() : null);
                jdbcTemplate.update(
                        "UPDATE public.candidate_submissions SET archived_at = NULL, status = 'PENDING', updated_at = NOW() WHERE id = ? OR (protocol IS NOT NULL AND protocol = ?)",
                        id, p
                );
            } catch (Exception ex) {
                log.debug("Erro ao restaurar candidate_submissions fallback: {}", ex.getMessage());
            }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CandidateSubmissionAdminService candidateSubmissionAdminService;

    @Override
    @Transactional
    public UUID promoteToModel(UUID id, Boolean activateImmediately) {
        log.info("Iniciando promoção da candidatura ID: {} para Modelo Oficial", id);
        if (candidateSubmissionAdminService != null) {
            try {
                return candidateSubmissionAdminService.promoteToModel(id, activateImmediately);
            } catch (ResourceNotFoundException ignored) {
                // Tenta fallback direto no modelRepository
            }
        }

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        model.setStatus(TalentLifecycleStatus.CAST_PUBLIC);
        model.setIsActive(Boolean.TRUE.equals(activateImmediately));
        model.setUpdatedAt(OffsetDateTime.now());
        modelRepository.save(model);

        log.info("Candidatura ID: {} promovida a modelo oficial com sucesso.", id);
        return model.getId();
    }

    private List<TalentLifecycleStatus> mapCandidateStatusToLifecycle(CandidateStatus status) {
        if (status == null) {
            return List.of(
                    TalentLifecycleStatus.APPLICANT_PENDING,
                    TalentLifecycleStatus.APPLICANT_APPROVED,
                    TalentLifecycleStatus.APPLICANT_DECLINED
            );
        }
        return switch (status) {
            case APPROVED, PROMOTED -> List.of(TalentLifecycleStatus.APPLICANT_APPROVED, TalentLifecycleStatus.CAST_PUBLIC);
            case REJECTED, DECLINED -> List.of(TalentLifecycleStatus.APPLICANT_DECLINED);
            case ARCHIVED -> List.of(TalentLifecycleStatus.ARCHIVED);
            default -> List.of(TalentLifecycleStatus.APPLICANT_PENDING);
        };
    }

    private CandidateStatus mapLifecycleToCandidateStatus(TalentLifecycleStatus status) {
        if (status == null) return CandidateStatus.PENDING;
        return switch (status) {
            case APPLICANT_APPROVED, CAST_INTERNAL, CAST_PUBLIC, CAST_STAR -> CandidateStatus.APPROVED;
            case APPLICANT_DECLINED -> CandidateStatus.REJECTED;
            case ARCHIVED -> CandidateStatus.ARCHIVED;
            default -> CandidateStatus.PENDING;
        };
    }

    private CandidateListItemAdminDto toListItemDto(Model model) {
        int age = calculateAge(model.getBirthDate(), model.getAge());
        boolean isMinor = age < 18;

        List<ModelMedia> media = modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(model.getId());
        int photoCount = media.size();

        String coverUrl = null;
        if (!media.isEmpty()) {
            ModelMedia first = media.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsCover()) || (m.getDisplayOrder() != null && (m.getDisplayOrder() == 1 || m.getDisplayOrder() == 0)))
                    .findFirst()
                    .orElse(media.get(0));
            coverUrl = resolveUrl(first.getFilePath(), first.getFileUrl());
        }
        if (!StringUtils.hasText(coverUrl) && StringUtils.hasText(model.getFacePhotoUrl())) {
            coverUrl = model.getFacePhotoUrl();
        }
        if (!StringUtils.hasText(coverUrl) && StringUtils.hasText(model.getPrimaryPhotoUrl())) {
            coverUrl = model.getPrimaryPhotoUrl();
        }

        BigDecimal heightCm = model.getHeightCm() != null ? BigDecimal.valueOf(model.getHeightCm()) : null;

        return CandidateListItemAdminDto.builder()
                .id(model.getId())
                .fullName(model.getFullName() != null ? model.getFullName() : model.getStageName())
                .email(model.getEmail())
                .phone(model.getPhone())
                .city(model.getCity())
                .state(model.getState())
                .age(age)
                .isMinor(isMinor)
                .gender(model.getGender() != null ? model.getGender().name() : "FEMALE")
                .heightCm(heightCm)
                .status(mapLifecycleToCandidateStatus(model.getStatus()))
                .photoCount(photoCount)
                .firstPhotoUrl(coverUrl)
                .coverPhoto(coverUrl)
                .coverPhotoUrl(coverUrl)
                .facePhotoUrl(coverUrl)
                .protocol(model.getProtocol())
                .createdAt(model.getCreatedAt())
                .build();
    }

    private CandidateDetailAdminDto toDetailDtoWithSignedUrls(Model model) {
        int age = calculateAge(model.getBirthDate(), model.getAge());
        boolean isMinor = age < 18;

        List<ModelMedia> mediaList = modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(model.getId());
        List<CandidatePhotoSignedDto> signedPhotos = mediaList.parallelStream()
                .map(media -> {
                    String url = resolveUrl(media.getFilePath(), media.getFileUrl());
                    int order = media.getDisplayOrder() != null ? media.getDisplayOrder() : 1;
                    return new CandidatePhotoSignedDto(
                            media.getId(),
                            order,
                            url,
                            (long) SIGNED_URL_EXPIRES_IN_SECONDS
                    );
                })
                .sorted(Comparator.comparing(CandidatePhotoSignedDto::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        BigDecimal heightCm = model.getHeightCm() != null ? BigDecimal.valueOf(model.getHeightCm()) : null;

        return CandidateDetailAdminDto.builder()
                .id(model.getId())
                .fullName(model.getFullName() != null ? model.getFullName() : model.getStageName())
                .email(model.getEmail())
                .phone(model.getPhone())
                .birthDate(model.getBirthDate())
                .age(age)
                .isMinor(isMinor)
                .gender(model.getGender() != null ? model.getGender().name() : "FEMALE")
                .heightCm(heightCm)
                .city(model.getCity())
                .state(model.getState())
                .legalGuardianName(model.getLegalGuardianName())
                .legalGuardianContact(model.getLegalGuardianContact())
                .weightKg(model.getWeightKg())
                .bustChestCm(model.getBustChestCm())
                .waistCm(model.getWaistCm())
                .hipsCm(model.getHipsCm())
                .shoeSize(model.getShoeSize())
                .dressSize(model.getDressSize())
                .instagramHandle(model.getInstagramUrl())
                .portfolioUrl(null)
                .tiktokHandle(null)
                .status(mapLifecycleToCandidateStatus(model.getStatus()))
                .internalNotes(model.getInternalNotes())
                .lgpdAccepted(model.getLgpdAccepted() != null ? model.getLgpdAccepted() : true)
                .convertedToModelId(model.getStatus() == TalentLifecycleStatus.CAST_PUBLIC ? model.getId() : null)
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .photos(signedPhotos)
                .build();
    }

    private String resolveUrl(String filePath, String fileUrl) {
        String bucket = supabaseProperties != null ? supabaseProperties.resolveBucketCandidates() : "candidates-uploads";
        String path = StringUtils.hasText(filePath) ? filePath.trim() : null;

        if (!StringUtils.hasText(path) && StringUtils.hasText(fileUrl)) {
            String trimmedUrl = fileUrl.trim();
            int bucketIdx = trimmedUrl.indexOf("candidates-uploads/");
            if (bucketIdx != -1) {
                path = trimmedUrl.substring(bucketIdx + "candidates-uploads/".length());
                int queryIdx = path.indexOf('?');
                if (queryIdx != -1) {
                    path = path.substring(0, queryIdx);
                }
            } else if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                path = trimmedUrl;
            }
        }

        if (StringUtils.hasText(path)) {
            try {
                return storageService.createSignedUrl(bucket, path, SIGNED_URL_EXPIRES_IN_SECONDS);
            } catch (Exception ex) {
                log.warn("Falha ao gerar Signed URL para bucket='{}', path='{}'. Usando fallback: {}",
                        bucket, path, ex.getMessage());
                return storageService.getPublicUrl(bucket, path);
            }
        }

        return fileUrl;
    }

    private int calculateAge(LocalDate birthDate, Integer explicitAge) {
        if (birthDate != null) {
            return Period.between(birthDate, LocalDate.now()).getYears();
        }
        return explicitAge != null ? explicitAge : 20;
    }

    @Override
    @Transactional
    public Map<String, Object> purgeArchive(boolean allArchived) {
        log.info("[ARCHIVE-PURGE] Iniciando expurgo do arquivo morto (allArchived={})", allArchived);
        int purgedCount = 0;

        // 1. Identificar e expurgar registros em candidate_submissions e candidate_photos
        if (jdbcTemplate != null) {
            try {
                String submissionsQuery = allArchived
                        ? "SELECT id, protocol FROM public.candidate_submissions WHERE archived_at IS NOT NULL OR status = 'ARCHIVED'"
                        : "SELECT id, protocol FROM public.candidate_submissions WHERE archived_at IS NOT NULL AND archived_at < NOW() - INTERVAL '7 days'";

                List<Map<String, Object>> rows = jdbcTemplate.queryForList(submissionsQuery);
                for (Map<String, Object> row : rows) {
                    Object idObj = row.get("id");
                    if (idObj == null) continue;
                    UUID subId = (idObj instanceof UUID) ? (UUID) idObj : UUID.fromString(idObj.toString());

                    // Expurga fotos no storage
                    List<String> photoPaths = jdbcTemplate.query(
                            "SELECT COALESCE(file_path, storage_path) AS file_path FROM public.candidate_photos WHERE candidate_id = ?",
                            (rs, rowNum) -> rs.getString("file_path"),
                            subId
                    );
                    for (String path : photoPaths) {
                        if (path != null && !path.isBlank()) {
                            try {
                                storageService.deleteFile("candidates-uploads", path);
                            } catch (Exception ignored) {}
                        }
                    }

                    // Apaga registros relacionais
                    jdbcTemplate.update("DELETE FROM public.candidate_photos WHERE candidate_id = ?", subId);
                    int deleted = jdbcTemplate.update("DELETE FROM public.candidate_submissions WHERE id = ?", subId);
                    purgedCount += deleted;
                }
            } catch (Exception ex) {
                log.error("[ARCHIVE-PURGE] Erro ao expurgar candidate_submissions: {}", ex.getMessage());
            }
        }

        // 2. Identificar e expurgar registros em models com status ARCHIVED
        try {
            List<Model> archivedModels;
            if (allArchived) {
                archivedModels = modelRepository.findAll().stream()
                        .filter(m -> m.getStatus() == TalentLifecycleStatus.ARCHIVED || m.getArchivedAt() != null)
                        .collect(Collectors.toList());
            } else {
                OffsetDateTime threshold = OffsetDateTime.now().minusDays(7);
                archivedModels = modelRepository.findByStatusArchivedBefore(threshold);
            }

            String modelsBucket = (supabaseProperties != null) ? supabaseProperties.resolveBucketCandidates() : "models-media";
            for (Model m : archivedModels) {
                if (m.getMedia() != null) {
                    for (ModelMedia media : m.getMedia()) {
                        String p = media.getFilePath() != null ? media.getFilePath() : media.getFileUrl();
                        if (p != null && !p.isBlank()) {
                            try {
                                storageService.deleteFile(modelsBucket, p);
                            } catch (Exception ignored) {}
                        }
                    }
                    modelMediaRepository.deleteAll(m.getMedia());
                }
                modelRepository.delete(m);
                purgedCount++;
            }
        } catch (Exception ex) {
            log.error("[ARCHIVE-PURGE] Erro ao expurgar models arquivados: {}", ex.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("purgedCount", purgedCount);
        response.put("message", "Expurgo manual concluído com sucesso! " + purgedCount + " registro(s) expurgado(s).");
        response.put("mode", "MANUAL");

        log.info("[ARCHIVE-PURGE] Expurgo manual concluído com sucesso. Total expurgado: {}", purgedCount);
        return response;
    }

    @Override
    @Transactional
    public int purgeArchivedSubmissions() {
        Map<String, Object> result = purgeArchive(true);
        Object countObj = result.get("purgedCount");
        if (countObj instanceof Number) {
            return ((Number) countObj).intValue();
        }
        return 0;
    }
}
