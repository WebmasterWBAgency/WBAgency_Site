package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
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
    private final com.wbscouting.api.repository.ModelRepository modelRepository;
    private final com.wbscouting.api.repository.ModelMediaRepository modelMediaRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StorageService storageService;

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

        if (decisionDto.getStatus() != null) {
            submission.setStatus(decisionDto.getStatus().name());
            if (decisionDto.getStatus() == SubmissionStatus.ARCHIVED) {
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
                m.setStatus(com.wbscouting.api.enums.TalentLifecycleStatus.APPLICANT_PENDING);
                m.setIsActive(true);
                m.setUpdatedAt(OffsetDateTime.now());
                modelRepository.save(m);
            });

            return CandidateDetailResponseDto.fromSubmission(saved, storageService);
        }

        com.wbscouting.api.entity.Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));
        model.setArchivedAt(null);
        model.setStatus(com.wbscouting.api.enums.TalentLifecycleStatus.APPLICANT_PENDING);
        model.setIsActive(true);
        model.setUpdatedAt(OffsetDateTime.now());
        modelRepository.save(model);

        return CandidateDetailResponseDto.fromModel(model, List.of(), storageService);
    }

    @Override
    @Transactional
    public UUID promoteToModel(UUID id, Boolean activateImmediately) {
        log.info("Iniciando promoção atômica da candidatura ID: {} para Modelo Oficial", id);

        java.util.Optional<CandidateSubmission> subOpt = candidateSubmissionRepository.findById(id);
        if (subOpt.isPresent()) {
            CandidateSubmission submission = subOpt.get();

            String stageName = deriveStageName(submission.getFullName());
            com.wbscouting.api.enums.GenderType gender = deriveGender(submission.getGender());

            String primaryPhoto = submission.getFacePhotoUrl();
            if ((primaryPhoto == null || primaryPhoto.isBlank()) && submission.getPhotos() != null && !submission.getPhotos().isEmpty()) {
                primaryPhoto = submission.getPhotos().get(0).getFileUrl();
                if (primaryPhoto == null || primaryPhoto.isBlank()) {
                    primaryPhoto = submission.getPhotos().get(0).getFilePath();
                }
            }

            Integer heightInt = submission.getHeight() != null ? submission.getHeight().intValue() : null;

            // 1. Persistir o Model com isActive = false (inativo para revisão interna) e elenco principal
            boolean activeFlag = Boolean.TRUE.equals(activateImmediately);
            com.wbscouting.api.entity.Model model = com.wbscouting.api.entity.Model.builder()
                    .stageName(stageName)
                    .fullName(submission.getFullName())
                    .email(submission.getEmail())
                    .phone(submission.getPhone())
                    .gender(gender)
                    .birthDate(submission.getBirthDate())
                    .heightCm(heightInt)
                    .bustChestCm(submission.getBust())
                    .waistCm(submission.getWaist())
                    .hipsCm(submission.getHips())
                    .shoeSize(submission.getShoeSize() != null ? submission.getShoeSize().toString() : null)
                    .eyesColor(submission.getEyeColor())
                    .hairColor(submission.getHairColor())
                    .city(submission.getCity())
                    .state(submission.getState())
                    .instagramUrl(submission.getInstagramHandle())
                    .legalGuardianName(submission.getGuardianName())
                    .legalGuardianContact(submission.getGuardianPhone())
                    .protocol(submission.getProtocol())
                    .status(com.wbscouting.api.enums.TalentLifecycleStatus.CAST_PUBLIC)
                    .isActive(activeFlag)
                    .isStar(false)
                    .isFeaturedHome(false)
                    .facePhotoUrl(submission.getFacePhotoUrl())
                    .profilePhotoUrl(submission.getProfilePhotoUrl())
                    .fullBodyPhotoUrl(submission.getFullBodyPhotoUrl())
                    .primaryPhotoUrl(primaryPhoto)
                    .lgpdAccepted(submission.getLgpdConsent() != null ? submission.getLgpdConsent() : true)
                    .build();

            com.wbscouting.api.entity.Model savedModel = modelRepository.save(model);

            // 2. Copiar fotos de candidate_photos para model_media, marcando a primeira foto frontal com is_cover = true e isActive = true
            if (submission.getPhotos() != null && !submission.getPhotos().isEmpty()) {
                boolean coverAssigned = false;
                for (int i = 0; i < submission.getPhotos().size(); i++) {
                    com.wbscouting.api.entity.CandidatePhoto cp = submission.getPhotos().get(i);
                    String fileUrl = cp.getFileUrl();
                    String filePath = cp.getFilePath() != null ? cp.getFilePath() : (cp.getStoragePath() != null ? cp.getStoragePath() : fileUrl);
                    if (fileUrl == null || fileUrl.isBlank()) {
                        fileUrl = filePath;
                    }

                    int order = cp.getDisplayOrder() != null ? cp.getDisplayOrder() : (i + 1);
                    boolean isCover = !coverAssigned && (order == 1 || (cp.getPhotoPosition() != null && cp.getPhotoPosition() == 1));
                    if (isCover) {
                        coverAssigned = true;
                    }

                    com.wbscouting.api.entity.ModelMedia media = com.wbscouting.api.entity.ModelMedia.builder()
                            .model(savedModel)
                            .mediaType(com.wbscouting.api.enums.MediaType.POLAROID)
                            .fileUrl(fileUrl != null ? fileUrl : "")
                            .filePath(filePath != null ? filePath : "")
                            .displayOrder(order)
                            .isCover(isCover)
                            .isActive(true)
                            .build();

                    modelMediaRepository.save(media);
                }
            } else if (primaryPhoto != null && !primaryPhoto.isBlank()) {
                com.wbscouting.api.entity.ModelMedia media = com.wbscouting.api.entity.ModelMedia.builder()
                        .model(savedModel)
                        .mediaType(com.wbscouting.api.enums.MediaType.POLAROID)
                        .fileUrl(primaryPhoto)
                        .filePath(primaryPhoto)
                        .displayOrder(1)
                        .isCover(true)
                        .isActive(true)
                        .build();
                modelMediaRepository.save(media);
            }

            // 3. Atualizar CandidateSubmission
            submission.setStatus("PROMOTED");
            submission.setReviewedAt(OffsetDateTime.now());
            candidateSubmissionRepository.save(submission);

            log.info("Candidatura ID: {} promovida com sucesso ao Modelo Oficial ID: {} (isActive={})",
                    id, savedModel.getId(), savedModel.getIsActive());
            return savedModel.getId();
        }

        // Fallback: caso o ID já pertença à tabela models (candidaturas legadas)
        java.util.Optional<com.wbscouting.api.entity.Model> modelOpt = modelRepository.findById(id);
        if (modelOpt.isPresent()) {
            com.wbscouting.api.entity.Model model = modelOpt.get();
            model.setStatus(com.wbscouting.api.enums.TalentLifecycleStatus.CAST_PUBLIC);
            model.setIsActive(Boolean.TRUE.equals(activateImmediately));
            model.setUpdatedAt(OffsetDateTime.now());
            modelRepository.save(model);
            log.info("Modelo existente ID: {} promovido/atualizado com sucesso (isActive={})", id, model.getIsActive());
            return model.getId();
        }

        throw new ResourceNotFoundException("Candidatura não encontrada com ID: " + id);
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

    private com.wbscouting.api.enums.GenderType deriveGender(String gender) {
        if (gender == null || gender.isBlank()) return com.wbscouting.api.enums.GenderType.FEMALE;
        String g = gender.trim().toUpperCase();
        if (g.startsWith("M") || g.contains("MASC")) {
            return com.wbscouting.api.enums.GenderType.MALE;
        }
        return com.wbscouting.api.enums.GenderType.FEMALE;
    }
}
