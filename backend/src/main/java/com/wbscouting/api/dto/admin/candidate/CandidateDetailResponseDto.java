package com.wbscouting.api.dto.admin.candidate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.enums.TalentLifecycleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateDetailResponseDto {

    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String instagram;
    private LocalDate birthDate;
    private Integer age;
    private Boolean isMinor;
    private String guardianName;
    private String guardianPhone;
    private String guardianEmail;
    private String city;
    private String state;
    private BiometricsDto biometrics;
    private List<CandidateMediaDto> photos;
    private SubmissionStatus status;
    private String internalNotes;
    private Boolean lgpdConsent;
    private OffsetDateTime lgpdConsentAt;
    private OffsetDateTime submittedAt;
    private String protocol;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BiometricsDto {
        private Integer height;
        private BigDecimal bust;
        private BigDecimal waist;
        private BigDecimal hips;
        private Integer shoes;
        private String eyes;
        private String hair;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateMediaDto {
        private String id;
        private String url;
        private String type;
        private String fileName;
        private Long fileSizeBytes;
    }

    public static CandidateDetailResponseDto fromModel(Model model) {
        return fromModel(model, model != null ? model.getMedia() : null);
    }

    public static CandidateDetailResponseDto fromModel(Model model, List<ModelMedia> mediaList) {
        if (model == null) return null;

        Integer heightVal = model.getHeight();

        Integer ageCalculated = null;
        boolean minor = false;
        LocalDate bd = model.getBirthDate();
        if (bd != null) {
            int anos = Period.between(bd, LocalDate.now()).getYears();
            if (anos >= 0 && anos < 120) {
                ageCalculated = anos;
                minor = anos < 18;
            }
        }

        BiometricsDto biometrics = BiometricsDto.builder()
                .height(heightVal)
                .bust(model.getBust())
                .waist(model.getWaist())
                .hips(model.getHips())
                .shoes(model.getShoes())
                .eyes("Não informado")
                .hair("Não informado")
                .build();

        List<CandidateMediaDto> photos = new ArrayList<>();
        List<ModelMedia> sourceMedia = (mediaList != null && !mediaList.isEmpty())
                ? mediaList
                : (model.getMedia() != null ? model.getMedia() : List.of());

        if (!sourceMedia.isEmpty()) {
            for (ModelMedia m : sourceMedia) {
                String u = m.getMediaUrl();
                if (u != null && !u.isBlank()) {
                    int pos = m.getDisplayOrder() != null ? m.getDisplayOrder() + 1 : 1;
                    String type = m.getMediaType() != null ? m.getMediaType().name() : switch (pos) {
                        case 1 -> "POLAROID_ROSTO";
                        case 2 -> "POLAROID_PERFIL";
                        case 3 -> "CORPO_INTEIRO";
                        default -> "POLAROID_" + pos;
                    };
                    photos.add(CandidateMediaDto.builder()
                            .id(m.getId() != null ? m.getId().toString() : "media-" + pos)
                            .url(u)
                            .type(type)
                            .fileName(extractFileName(u, "photo-" + pos + ".jpg"))
                            .fileSizeBytes(1500000L)
                            .build());
                }
            }
        } else if (model.getPrimaryPhotoUrl() != null && !model.getPrimaryPhotoUrl().isBlank()) {
            photos.add(CandidateMediaDto.builder()
                    .id("photo-primary-" + model.getId())
                    .url(model.getPrimaryPhotoUrl())
                    .type("POLAROID_ROSTO")
                    .fileName(extractFileName(model.getPrimaryPhotoUrl(), "primary-photo.jpg"))
                    .fileSizeBytes(1200000L)
                    .build());
        }

        SubmissionStatus subStatus = SubmissionStatus.PENDING;
        if (model.getStatus() != null) {
            subStatus = switch (model.getStatus()) {
                case APPLICANT_APPROVED -> SubmissionStatus.APPROVED;
                case APPLICANT_DECLINED -> SubmissionStatus.DECLINED;
                case ARCHIVED -> SubmissionStatus.ARCHIVED;
                case CAST_PUBLIC, CAST_INTERNAL, CAST_STAR -> SubmissionStatus.PROMOTED;
                default -> SubmissionStatus.PENDING;
            };
        }

        return CandidateDetailResponseDto.builder()
                .id(model.getId())
                .fullName(model.getName())
                .email(model.getEmail())
                .phone(model.getPhone())
                .instagram(model.getInstagram())
                .birthDate(bd)
                .age(ageCalculated)
                .isMinor(minor)
                .guardianName(model.getGuardianName())
                .guardianPhone(model.getGuardianPhone())
                .guardianEmail(model.getGuardianEmail())
                .city(model.getCity())
                .state(model.getState())
                .biometrics(biometrics)
                .photos(photos)
                .status(subStatus)
                .internalNotes(model.getInternalNotes())
                .lgpdConsent(true)
                .lgpdConsentAt(model.getCreatedAt())
                .submittedAt(model.getCreatedAt())
                .protocol(model.getProtocol())
                .build();
    }

    private static String extractFileName(String url, String fallback) {
        if (url == null || url.isBlank()) return fallback;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            int queryParam = name.indexOf('?');
            return queryParam > 0 ? name.substring(0, queryParam) : name;
        }
        return fallback;
    }
}
