package com.wbscouting.api.service.candidate;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidateListItemAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidatePhotoSignedDto;
import com.wbscouting.api.dto.common.PageResponseDto;
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
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class AdminCandidateQueryServiceImpl implements AdminCandidateQueryService {

    public static final int SIGNED_URL_EXPIRES_IN_SECONDS = 900;

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final StorageService storageService;
    private final SupabaseProperties supabaseProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<CandidateListItemAdminDto> listCandidates(
            CandidateStatus status,
            String gender,
            String search,
            Boolean isMinor,
            int page,
            int size) {

        int validatedPage = Math.max(page, 0);
        int validatedSize = (size <= 0) ? 20 : size;
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return listCandidates(status, gender, search, isMinor, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<CandidateListItemAdminDto> listCandidates(
            CandidateStatus status,
            String gender,
            String search,
            Boolean isMinor,
            Pageable pageable) {

        log.info("Executando consulta paginada de candidatos sobre Model: status={}, gender={}, search='{}', isMinor={}",
                status, gender, search, isMinor);

        List<TalentLifecycleStatus> statuses = mapCandidateStatusToLifecycle(status);

        Page<Model> modelPage = modelRepository.findAdminWithFiltersAndStatuses(
                null, null, null, statuses, (search != null && !search.isBlank()) ? search.trim() : null, pageable
        );

        if (modelPage.isEmpty()) {
            return PageResponseDto.from(Page.empty(pageable));
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

        Page<CandidateListItemAdminDto> dtoPage = new PageImpl<>(dtoList, pageable, modelPage.getTotalElements());
        return PageResponseDto.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateDetailAdminDto getCandidateDetail(UUID id) {
        log.info("Consultando detalhes completos da candidatura ID: {}", id);

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada com ID: " + id));

        return toDetailDtoWithSignedUrls(model);
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
            coverUrl = resolveUrl(null, model.getFacePhotoUrl());
        }
        if (!StringUtils.hasText(coverUrl) && StringUtils.hasText(model.getPrimaryPhotoUrl())) {
            coverUrl = resolveUrl(null, model.getPrimaryPhotoUrl());
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

        if (signedPhotos.isEmpty()) {
            if (StringUtils.hasText(model.getFacePhotoUrl())) {
                signedPhotos.add(CandidatePhotoSignedDto.builder()
                        .id(UUID.randomUUID())
                        .displayOrder(1)
                        .signedUrl(resolveUrl(null, model.getFacePhotoUrl()))
                        .build());
            } else if (StringUtils.hasText(model.getPrimaryPhotoUrl())) {
                signedPhotos.add(CandidatePhotoSignedDto.builder()
                        .id(UUID.randomUUID())
                        .displayOrder(1)
                        .signedUrl(resolveUrl(null, model.getPrimaryPhotoUrl()))
                        .build());
            }
            if (StringUtils.hasText(model.getProfilePhotoUrl())) {
                signedPhotos.add(CandidatePhotoSignedDto.builder()
                        .id(UUID.randomUUID())
                        .displayOrder(2)
                        .signedUrl(resolveUrl(null, model.getProfilePhotoUrl()))
                        .build());
            }
            if (StringUtils.hasText(model.getFullBodyPhotoUrl())) {
                signedPhotos.add(CandidatePhotoSignedDto.builder()
                        .id(UUID.randomUUID())
                        .displayOrder(3)
                        .signedUrl(resolveUrl(null, model.getFullBodyPhotoUrl()))
                        .build());
            }
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
    public void deleteCandidate(UUID id) {
        log.warn("Solicitação de expurgo de candidato ID: {}", id);
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidato não encontrado: " + id));

        List<ModelMedia> mediaList = modelMediaRepository.findAllByModelIdOrderByDisplayOrderAsc(id);
        String bucket = supabaseProperties != null ? supabaseProperties.resolveBucketCandidates() : "models-media";
        for (ModelMedia m : mediaList) {
            String path = StringUtils.hasText(m.getFilePath()) ? m.getFilePath() : m.getFileUrl();
            if (path != null && !path.startsWith("http")) {
                try {
                    storageService.deleteFile(bucket, path);
                } catch (Exception ignored) {}
            }
        }
        modelMediaRepository.deleteAll(mediaList);
        modelRepository.delete(model);
        log.info("Candidato e fotos físicas expurgados com sucesso: {}", id);
    }
}
