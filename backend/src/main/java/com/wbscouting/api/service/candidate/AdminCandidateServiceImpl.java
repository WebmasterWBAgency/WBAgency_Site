package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.admin.candidate.*;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.CandidateStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
    public void deleteCandidate(UUID id) {
        log.info("Iniciando arquivamento (soft delete) da candidatura ID: {}", id);

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        model.setStatus(TalentLifecycleStatus.ARCHIVED);
        model.setUpdatedAt(OffsetDateTime.now());
        modelRepository.save(model);
        log.info("Candidatura ID: {} movida para Arquivo Morto com sucesso.", id);
    }

    @Override
    @Transactional
    public UUID promoteToModel(UUID id, Boolean activateImmediately) {
        log.info("Iniciando promoção da candidatura ID: {} para Modelo Oficial", id);

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        model.setStatus(TalentLifecycleStatus.CAST_PUBLIC);
        model.setIsActive(activateImmediately == null || Boolean.TRUE.equals(activateImmediately));
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
            ModelMedia first = media.stream().filter(ModelMedia::getIsCover).findFirst().orElse(media.get(0));
            coverUrl = resolveUrl(first.getFilePath(), first.getFileUrl());
        } else if (model.getPrimaryPhotoUrl() != null) {
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
                .createdAt(model.getCreatedAt())
                .build();
    }

    private CandidateDetailAdminDto toDetailDtoWithSignedUrls(Model model) {
        int age = calculateAge(model.getBirthDate(), model.getAge());
        boolean isMinor = age < 18;

        List<ModelMedia> mediaList = modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(model.getId());
        List<CandidatePhotoSignedDto> signedPhotos = new ArrayList<>();

        for (int i = 0; i < mediaList.size(); i++) {
            ModelMedia media = mediaList.get(i);
            String url = resolveUrl(media.getFilePath(), media.getFileUrl());
            signedPhotos.add(CandidatePhotoSignedDto.builder()
                    .id(media.getId())
                    .displayOrder(media.getDisplayOrder() != null ? media.getDisplayOrder() : i + 1)
                    .signedUrl(url)
                    .build());
        }

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
        if (StringUtils.hasText(fileUrl) && (fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))) {
            return fileUrl;
        }
        String bucket = supabaseProperties != null ? supabaseProperties.resolveBucketCandidates() : "models-media";
        String path = StringUtils.hasText(filePath) ? filePath : fileUrl;
        try {
            return storageService.createSignedUrl(bucket, path, SIGNED_URL_EXPIRES_IN_SECONDS);
        } catch (Exception ex) {
            return storageService.getPublicUrl(bucket, path);
        }
    }

    private int calculateAge(LocalDate birthDate, Integer explicitAge) {
        if (birthDate != null) {
            return Period.between(birthDate, LocalDate.now()).getYears();
        }
        return explicitAge != null ? explicitAge : 20;
    }
}
