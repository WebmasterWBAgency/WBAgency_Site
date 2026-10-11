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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateApplicationSummaryDto {

    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private Integer age;
    private Boolean isMinor;
    private String city;
    private String state;
    private Integer height;
    private BigDecimal bust;
    private BigDecimal waist;
    private BigDecimal hips;
    private Integer shoes;
    private SubmissionStatus status;
    private Boolean hasPhotos;
    private Integer polaroidsCount;
    private OffsetDateTime createdAt;
    private String coverPhoto;
    private String coverPhotoUrl;
    private String facePhotoUrl;
    private String profilePhotoUrl;
    private String fullBodyPhotoUrl;
    private String protocol;
    private List<PhotoThumbDto> photos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoThumbDto {
        private String id;
        private String url;
        private String type;

        public static PhotoThumbDtoBuilder builder() { return new PhotoThumbDtoBuilder(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public static class PhotoThumbDtoBuilder {
            private final PhotoThumbDto p = new PhotoThumbDto();
            public PhotoThumbDtoBuilder id(String v) { p.setId(v); return this; }
            public PhotoThumbDtoBuilder url(String v) { p.setUrl(v); return this; }
            public PhotoThumbDtoBuilder type(String v) { p.setType(v); return this; }
            public PhotoThumbDto build() { return p; }
        }
    }

    public static CandidateApplicationSummaryDtoBuilder builder() { return new CandidateApplicationSummaryDtoBuilder(); }

    public static CandidateApplicationSummaryDto fromModel(Model model) {
        return fromModel(model, model != null ? model.getMedia() : null);
    }

    public static CandidateApplicationSummaryDto fromModel(Model model, List<ModelMedia> mediaList) {
        if (model == null) return null;

        List<ModelMedia> media = (mediaList != null && !mediaList.isEmpty())
                ? mediaList
                : (model.getMedia() != null ? model.getMedia() : List.of());

        String face = null;
        String profile = null;
        String fullBody = null;
        List<PhotoThumbDto> photosArr = new ArrayList<>();

        if (!media.isEmpty()) {
            for (ModelMedia m : media) {
                String u = m.getMediaUrl();
                if (u != null && !u.isBlank()) {
                    int pos = m.getDisplayOrder() != null ? m.getDisplayOrder() + 1 : 1;
                    String type = m.getMediaType() != null ? m.getMediaType().name() : switch (pos) {
                        case 1 -> "POLAROID_ROSTO";
                        case 2 -> "POLAROID_PERFIL";
                        case 3 -> "CORPO_INTEIRO";
                        default -> "POLAROID_" + pos;
                    };

                    if (pos == 1 && face == null) face = u;
                    else if (pos == 2 && profile == null) profile = u;
                    else if (pos == 3 && fullBody == null) fullBody = u;

                    photosArr.add(PhotoThumbDto.builder()
                            .id(m.getId() != null ? m.getId().toString() : "m-" + pos)
                            .url(u)
                            .type(type)
                            .build());
                }
            }
        }

        if (face == null && model.getPrimaryPhotoUrl() != null && !model.getPrimaryPhotoUrl().isBlank()) {
            face = model.getPrimaryPhotoUrl();
        }

        String cover = face != null ? face : (!photosArr.isEmpty() ? photosArr.get(0).getUrl() : null);

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

        return CandidateApplicationSummaryDto.builder()
                .id(model.getId())
                .fullName(model.getName())
                .email(model.getEmail())
                .phone(model.getPhone())
                .birthDate(bd)
                .age(ageCalculated)
                .isMinor(minor)
                .city(model.getCity())
                .state(model.getState())
                .height(model.getHeight())
                .bust(model.getBust())
                .waist(model.getWaist())
                .hips(model.getHips())
                .shoes(model.getShoes())
                .status(subStatus)
                .hasPhotos(cover != null || !photosArr.isEmpty())
                .polaroidsCount(!photosArr.isEmpty() ? photosArr.size() : (cover != null ? 1 : 0))
                .createdAt(model.getCreatedAt())
                .coverPhoto(cover)
                .coverPhotoUrl(cover)
                .facePhotoUrl(face != null ? face : cover)
                .profilePhotoUrl(profile)
                .fullBodyPhotoUrl(fullBody)
                .photos(photosArr.isEmpty() ? null : photosArr)
                .protocol(model.getProtocol())
                .build();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ===================== GETTERS =====================
    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getBirthDate() { return birthDate; }
    public Integer getAge() { return age; }
    public Boolean getIsMinor() { return isMinor; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public Integer getHeight() { return height; }
    public BigDecimal getBust() { return bust; }
    public BigDecimal getWaist() { return waist; }
    public BigDecimal getHips() { return hips; }
    public Integer getShoes() { return shoes; }
    public SubmissionStatus getStatus() { return status; }
    public Boolean getHasPhotos() { return hasPhotos; }
    public Integer getPolaroidsCount() { return polaroidsCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getCoverPhoto() { return coverPhoto; }
    public String getCoverPhotoUrl() { return coverPhotoUrl != null ? coverPhotoUrl : coverPhoto; }
    public String getFacePhotoUrl() { return facePhotoUrl; }
    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public String getFullBodyPhotoUrl() { return fullBodyPhotoUrl; }
    public String getProtocol() { return protocol; }
    public List<PhotoThumbDto> getPhotos() { return photos; }

    // ===================== SETTERS =====================
    public void setId(UUID id) { this.id = id; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public void setAge(Integer age) { this.age = age; }
    public void setIsMinor(Boolean isMinor) { this.isMinor = isMinor; }
    public void setCity(String city) { this.city = city; }
    public void setState(String state) { this.state = state; }
    public void setHeight(Integer height) { this.height = height; }
    public void setBust(BigDecimal bust) { this.bust = bust; }
    public void setWaist(BigDecimal waist) { this.waist = waist; }
    public void setHips(BigDecimal hips) { this.hips = hips; }
    public void setShoes(Integer shoes) { this.shoes = shoes; }
    public void setStatus(SubmissionStatus status) { this.status = status; }
    public void setHasPhotos(Boolean hasPhotos) { this.hasPhotos = hasPhotos; }
    public void setPolaroidsCount(Integer polaroidsCount) { this.polaroidsCount = polaroidsCount; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setCoverPhoto(String coverPhoto) { this.coverPhoto = coverPhoto; }
    public void setCoverPhotoUrl(String coverPhotoUrl) { this.coverPhotoUrl = coverPhotoUrl; }
    public void setFacePhotoUrl(String facePhotoUrl) { this.facePhotoUrl = facePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }
    public void setFullBodyPhotoUrl(String fullBodyPhotoUrl) { this.fullBodyPhotoUrl = fullBodyPhotoUrl; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public void setPhotos(List<PhotoThumbDto> photos) { this.photos = photos; }

    // ===================== BUILDER =====================
    public static class CandidateApplicationSummaryDtoBuilder {
        private final CandidateApplicationSummaryDto d = new CandidateApplicationSummaryDto();
        public CandidateApplicationSummaryDtoBuilder id(UUID v) { d.setId(v); return this; }
        public CandidateApplicationSummaryDtoBuilder fullName(String v) { d.setFullName(v); return this; }
        public CandidateApplicationSummaryDtoBuilder email(String v) { d.setEmail(v); return this; }
        public CandidateApplicationSummaryDtoBuilder phone(String v) { d.setPhone(v); return this; }
        public CandidateApplicationSummaryDtoBuilder birthDate(LocalDate v) { d.setBirthDate(v); return this; }
        public CandidateApplicationSummaryDtoBuilder age(Integer v) { d.setAge(v); return this; }
        public CandidateApplicationSummaryDtoBuilder isMinor(Boolean v) { d.setIsMinor(v); return this; }
        public CandidateApplicationSummaryDtoBuilder city(String v) { d.setCity(v); return this; }
        public CandidateApplicationSummaryDtoBuilder state(String v) { d.setState(v); return this; }
        public CandidateApplicationSummaryDtoBuilder height(Integer v) { d.setHeight(v); return this; }
        public CandidateApplicationSummaryDtoBuilder bust(BigDecimal v) { d.setBust(v); return this; }
        public CandidateApplicationSummaryDtoBuilder waist(BigDecimal v) { d.setWaist(v); return this; }
        public CandidateApplicationSummaryDtoBuilder hips(BigDecimal v) { d.setHips(v); return this; }
        public CandidateApplicationSummaryDtoBuilder shoes(Integer v) { d.setShoes(v); return this; }
        public CandidateApplicationSummaryDtoBuilder status(SubmissionStatus v) { d.setStatus(v); return this; }
        public CandidateApplicationSummaryDtoBuilder hasPhotos(Boolean v) { d.setHasPhotos(v); return this; }
        public CandidateApplicationSummaryDtoBuilder polaroidsCount(Integer v) { d.setPolaroidsCount(v); return this; }
        public CandidateApplicationSummaryDtoBuilder createdAt(OffsetDateTime v) { d.setCreatedAt(v); return this; }
        public CandidateApplicationSummaryDtoBuilder coverPhoto(String v) { d.setCoverPhoto(v); return this; }
        public CandidateApplicationSummaryDtoBuilder coverPhotoUrl(String v) { d.setCoverPhotoUrl(v); return this; }
        public CandidateApplicationSummaryDtoBuilder facePhotoUrl(String v) { d.setFacePhotoUrl(v); return this; }
        public CandidateApplicationSummaryDtoBuilder profilePhotoUrl(String v) { d.setProfilePhotoUrl(v); return this; }
        public CandidateApplicationSummaryDtoBuilder fullBodyPhotoUrl(String v) { d.setFullBodyPhotoUrl(v); return this; }
        public CandidateApplicationSummaryDtoBuilder photos(List<PhotoThumbDto> v) { d.setPhotos(v); return this; }
        public CandidateApplicationSummaryDtoBuilder protocol(String v) { d.setProtocol(v); return this; }
        public CandidateApplicationSummaryDto build() { return d; }
    }
}
