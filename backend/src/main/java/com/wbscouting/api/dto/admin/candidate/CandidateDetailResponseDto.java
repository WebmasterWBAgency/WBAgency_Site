package com.wbscouting.api.dto.admin.candidate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.SubmissionStatus;
import com.wbscouting.api.service.storage.StorageService;
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
    @Builder.Default
    private List<CandidateMediaDto> photos = new ArrayList<>();
    private SubmissionStatus status;
    private String internalNotes;
    private Boolean lgpdConsent;
    private OffsetDateTime lgpdConsentAt;
    private String coverPhoto;
    private String coverPhotoUrl;
    private String facePhotoUrl;
    private String profilePhotoUrl;
    private String fullBodyPhotoUrl;
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
        private Boolean isFromApply;
        private Boolean isLowResolution;
    }

    public static CandidateDetailResponseDto fromModel(Model model) {
        return fromModel(model, model != null ? model.getMedia() : null, null);
    }

    public static CandidateDetailResponseDto fromModel(Model model, List<ModelMedia> mediaList) {
        return fromModel(model, mediaList, null);
    }

    public static CandidateDetailResponseDto fromModel(Model model, List<ModelMedia> mediaList, StorageService storageService) {
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

        String face = (model.getFacePhotoUrl() != null && !model.getFacePhotoUrl().isBlank())
                ? model.getFacePhotoUrl().trim()
                : (model.getPrimaryPhotoUrl() != null && !model.getPrimaryPhotoUrl().isBlank() ? model.getPrimaryPhotoUrl().trim() : null);
        String profile = (model.getProfilePhotoUrl() != null && !model.getProfilePhotoUrl().isBlank())
                ? model.getProfilePhotoUrl().trim()
                : null;
        String fullBody = (model.getFullBodyPhotoUrl() != null && !model.getFullBodyPhotoUrl().isBlank())
                ? model.getFullBodyPhotoUrl().trim()
                : null;

        if (storageService != null) {
            face = resolveDirectPhotoUrl(face, storageService);
            profile = resolveDirectPhotoUrl(profile, storageService);
            fullBody = resolveDirectPhotoUrl(fullBody, storageService);
        }

        List<CandidateMediaDto> photos = new ArrayList<>();
        List<ModelMedia> sourceMedia = (mediaList != null && !mediaList.isEmpty())
                ? mediaList
                : (model.getPhotos() != null && !model.getPhotos().isEmpty()
                    ? model.getPhotos()
                    : (model.getMedia() != null ? model.getMedia() : List.of()));

        if (!sourceMedia.isEmpty()) {
            for (int i = 0; i < sourceMedia.size(); i++) {
                ModelMedia m = sourceMedia.get(i);
                String u = resolveMediaUrl(m, storageService);
                if (u != null && !u.isBlank()) {
                    int order = m.getDisplayOrder() != null ? m.getDisplayOrder() : i + 1;
                    int pos = (order == 0) ? 1 : order;
                    String type = m.getMediaType() != null ? m.getMediaType().name() : switch (pos) {
                        case 1 -> "POLAROID_ROSTO";
                        case 2 -> "POLAROID_PERFIL";
                        case 3 -> "CORPO_INTEIRO";
                        default -> "POLAROID_" + pos;
                    };
                    if (pos == 1 && face == null) face = u;
                    else if (pos == 2 && profile == null) profile = u;
                    else if (pos == 3 && fullBody == null) fullBody = u;

                    boolean fromApply = (u != null && u.contains("candidates-uploads"))
                            || (m.getFilePath() != null && m.getFilePath().contains("candidates-uploads"));

                    photos.add(CandidateMediaDto.builder()
                            .id(m.getId() != null ? m.getId().toString() : "media-" + pos)
                            .url(u)
                            .type(type)
                            .fileName(extractFileName(u, "photo-" + pos + ".jpg"))
                            .fileSizeBytes(1500000L)
                            .isFromApply(fromApply)
                            .isLowResolution(fromApply)
                            .build());
                }
            }
        }

        // Fallback: se photos ainda estiver vazio, sintetiza a partir das colunas diretas da candidatura
        if (photos.isEmpty()) {
            if (face != null) {
                boolean fromApply = face.contains("candidates-uploads");
                photos.add(CandidateMediaDto.builder()
                        .id("photo-face-" + model.getId())
                        .url(face)
                        .type("POLAROID_ROSTO")
                        .fileName(extractFileName(face, "polaroid-rosto.jpg"))
                        .fileSizeBytes(1500000L)
                        .isFromApply(fromApply)
                        .isLowResolution(fromApply)
                        .build());
            }
            if (profile != null) {
                boolean fromApply = profile.contains("candidates-uploads");
                photos.add(CandidateMediaDto.builder()
                        .id("photo-profile-" + model.getId())
                        .url(profile)
                        .type("POLAROID_PERFIL")
                        .fileName(extractFileName(profile, "polaroid-perfil.jpg"))
                        .fileSizeBytes(1500000L)
                        .isFromApply(fromApply)
                        .isLowResolution(fromApply)
                        .build());
            }
            if (fullBody != null) {
                boolean fromApply = fullBody.contains("candidates-uploads");
                photos.add(CandidateMediaDto.builder()
                        .id("photo-fullbody-" + model.getId())
                        .url(fullBody)
                        .type("CORPO_INTEIRO")
                        .fileName(extractFileName(fullBody, "polaroid-corpo-inteiro.jpg"))
                        .fileSizeBytes(1500000L)
                        .isFromApply(fromApply)
                        .isLowResolution(fromApply)
                        .build());
            }
        }

        String cover = face != null ? face : (!photos.isEmpty() ? photos.get(0).getUrl() : null);
        if (face == null && cover != null) {
            face = cover;
        }

        SubmissionStatus subStatus = SubmissionStatus.PENDING;
        if (model.getArchivedAt() != null || model.getStatus() == com.wbscouting.api.enums.TalentLifecycleStatus.ARCHIVED) {
            subStatus = SubmissionStatus.ARCHIVED;
        } else if (model.getStatus() != null) {
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
                .coverPhoto(cover)
                .coverPhotoUrl(cover)
                .facePhotoUrl(face)
                .profilePhotoUrl(profile)
                .fullBodyPhotoUrl(fullBody)
                .build();
    }

    public static CandidateDetailResponseDto fromSubmission(com.wbscouting.api.entity.CandidateSubmission submission) {
        return fromSubmission(submission, null);
    }

    public static CandidateDetailResponseDto fromSubmission(com.wbscouting.api.entity.CandidateSubmission submission, StorageService storageService) {
        if (submission == null) return null;

        Integer heightVal = submission.getHeight() != null ? submission.getHeight().intValue() : null;

        Integer ageCalculated = null;
        boolean minor = false;
        LocalDate bd = submission.getBirthDate();
        if (bd != null) {
            int anos = Period.between(bd, LocalDate.now()).getYears();
            if (anos >= 0 && anos < 120) {
                ageCalculated = anos;
                minor = anos < 18;
            }
        } else if (submission.getAge() != null) {
            ageCalculated = submission.getAge();
            minor = ageCalculated < 18;
        }

        BiometricsDto biometrics = BiometricsDto.builder()
                .height(heightVal)
                .bust(submission.getBust() != null ? BigDecimal.valueOf(submission.getBust()) : null)
                .waist(submission.getWaist() != null ? BigDecimal.valueOf(submission.getWaist()) : null)
                .hips(submission.getHips() != null ? BigDecimal.valueOf(submission.getHips()) : null)
                .shoes(submission.getShoeSize())
                .eyes(submission.getEyeColor())
                .hair(submission.getHairColor())
                .build();

        String face = (submission.getFacePhotoUrl() != null && !submission.getFacePhotoUrl().isBlank())
                ? submission.getFacePhotoUrl().trim()
                : null;
        String profile = (submission.getProfilePhotoUrl() != null && !submission.getProfilePhotoUrl().isBlank())
                ? submission.getProfilePhotoUrl().trim()
                : null;
        String fullBody = (submission.getFullBodyPhotoUrl() != null && !submission.getFullBodyPhotoUrl().isBlank())
                ? submission.getFullBodyPhotoUrl().trim()
                : null;

        if (storageService != null) {
            String signedFace = storageService.createSignedUrl("candidates-uploads", face, 900);
            if (signedFace != null) face = signedFace;

            String signedProfile = storageService.createSignedUrl("candidates-uploads", profile, 900);
            if (signedProfile != null) profile = signedProfile;

            String signedFullBody = storageService.createSignedUrl("candidates-uploads", fullBody, 900);
            if (signedFullBody != null) fullBody = signedFullBody;
        }

        List<CandidateMediaDto> photos = new ArrayList<>();
        if (submission.getPhotos() != null && !submission.getPhotos().isEmpty()) {
            List<com.wbscouting.api.entity.CandidatePhoto> cpList = submission.getPhotos();
            for (int i = 0; i < cpList.size(); i++) {
                com.wbscouting.api.entity.CandidatePhoto cp = cpList.get(i);
                String u = cp.getFileUrl();
                if (u == null || u.isBlank()) u = cp.getFilePath();
                if (u == null || u.isBlank()) u = cp.getStoragePath();

                if (u != null && !u.isBlank()) {
                    String photoUrl = u.trim();
                    if (storageService != null) {
                        String signed = storageService.createSignedUrl("candidates-uploads", photoUrl, 900);
                        if (signed != null) photoUrl = signed;
                    }

                    int order = cp.getDisplayOrder() != null ? cp.getDisplayOrder() : i + 1;
                    int pos = (order == 0) ? 1 : order;
                    String type = switch (pos) {
                        case 1 -> "POLAROID_ROSTO";
                        case 2 -> "POLAROID_PERFIL";
                        case 3 -> "CORPO_INTEIRO";
                        default -> "POLAROID_" + pos;
                    };

                    if (pos == 1 && face == null) face = photoUrl;
                    else if (pos == 2 && profile == null) profile = photoUrl;
                    else if (pos == 3 && fullBody == null) fullBody = photoUrl;

                    photos.add(CandidateMediaDto.builder()
                            .id(cp.getId() != null ? cp.getId().toString() : "p-" + pos)
                            .url(photoUrl)
                            .type(type)
                            .fileName("polaroid-" + pos + ".jpg")
                            .fileSizeBytes(1500000L)
                            .isFromApply(true)
                            .isLowResolution(true)
                            .build());
                }
            }
        }

        if (photos.isEmpty()) {
            if (face != null) {
                photos.add(CandidateMediaDto.builder()
                        .id("p-1").url(face).type("POLAROID_ROSTO")
                        .fileName(extractFileName(face, "polaroid-rosto.jpg")).fileSizeBytes(1500000L)
                        .isFromApply(true).isLowResolution(true).build());
            }
            if (profile != null) {
                photos.add(CandidateMediaDto.builder()
                        .id("p-2").url(profile).type("POLAROID_PERFIL")
                        .fileName(extractFileName(profile, "polaroid-perfil.jpg")).fileSizeBytes(1500000L)
                        .isFromApply(true).isLowResolution(true).build());
            }
            if (fullBody != null) {
                photos.add(CandidateMediaDto.builder()
                        .id("p-3").url(fullBody).type("CORPO_INTEIRO")
                        .fileName(extractFileName(fullBody, "polaroid-corpo-inteiro.jpg")).fileSizeBytes(1500000L)
                        .isFromApply(true).isLowResolution(true).build());
            }
        }

        String cover = face != null ? face : (!photos.isEmpty() ? photos.get(0).getUrl() : null);
        if (face == null && cover != null) {
            face = cover;
        }

        SubmissionStatus subStatus = SubmissionStatus.PENDING;
        if (submission.getArchivedAt() != null || "ARCHIVED".equalsIgnoreCase(submission.getStatus())) {
            subStatus = SubmissionStatus.ARCHIVED;
        } else if (submission.getStatus() != null) {
            try {
                subStatus = SubmissionStatus.valueOf(submission.getStatus().trim().toUpperCase());
            } catch (Exception ignored) {
                if ("DECLINED".equalsIgnoreCase(submission.getStatus())) {
                    subStatus = SubmissionStatus.DECLINED;
                }
            }
        }

        return CandidateDetailResponseDto.builder()
                .id(submission.getId())
                .fullName(submission.getFullName())
                .email(submission.getEmail())
                .phone(submission.getPhone())
                .instagram(submission.getInstagramHandle())
                .birthDate(bd)
                .age(ageCalculated)
                .isMinor(minor)
                .guardianName(submission.getGuardianName())
                .guardianPhone(submission.getGuardianPhone())
                .guardianEmail(submission.getGuardianEmail())
                .city(submission.getCity())
                .state(submission.getState())
                .biometrics(biometrics)
                .photos(photos)
                .status(subStatus)
                .internalNotes(submission.getFeedbackNotes())
                .lgpdConsent(submission.getLgpdConsent() != null ? submission.getLgpdConsent() : true)
                .lgpdConsentAt(submission.getLgpdConsentAt() != null ? submission.getLgpdConsentAt() : submission.getCreatedAt())
                .submittedAt(submission.getCreatedAt())
                .protocol(submission.getProtocol())
                .coverPhoto(cover)
                .coverPhotoUrl(cover)
                .facePhotoUrl(face)
                .profilePhotoUrl(profile)
                .fullBodyPhotoUrl(fullBody)
                .build();
    }

    private static String resolveMediaUrl(ModelMedia m, StorageService storageService) {
        if (m == null) return null;
        if (storageService != null) {
            String path = (m.getFilePath() != null && !m.getFilePath().isBlank()) ? m.getFilePath().trim() : null;
            String rawUrl = m.getFileUrl();
            if (path == null && rawUrl != null && !rawUrl.isBlank()) {
                int idx = rawUrl.indexOf("candidates-uploads/");
                if (idx != -1) {
                    path = rawUrl.substring(idx + "candidates-uploads/".length());
                    int q = path.indexOf('?');
                    if (q != -1) path = path.substring(0, q);
                } else if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    path = rawUrl.trim();
                }
            }
            if (path != null && !path.isBlank()) {
                try {
                    return storageService.createSignedUrl("candidates-uploads", path, 900);
                } catch (Exception ignored) {
                }
            }
        }
        return extractMediaUrl(m);
    }

    private static String resolveDirectPhotoUrl(String photoUrl, StorageService storageService) {
        if (photoUrl == null || photoUrl.isBlank()) return null;
        String trimmed = photoUrl.trim();
        if (storageService != null) {
            String path = null;
            int idx = trimmed.indexOf("candidates-uploads/");
            if (idx != -1) {
                path = trimmed.substring(idx + "candidates-uploads/".length());
                int q = path.indexOf('?');
                if (q != -1) path = path.substring(0, q);
            } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                path = trimmed;
            }
            if (path != null && !path.isBlank()) {
                try {
                    return storageService.createSignedUrl("candidates-uploads", path, 900);
                } catch (Exception ignored) {
                }
            }
        }
        return trimmed;
    }

    private static String extractMediaUrl(ModelMedia m) {
        if (m == null) return null;
        String u = m.getFileUrl();
        if (u == null || u.isBlank()) {
            u = m.getFilePath();
        }
        if (u == null || u.isBlank()) {
            u = m.getMediaUrl();
        }
        return (u != null && !u.isBlank()) ? u.trim() : null;
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
