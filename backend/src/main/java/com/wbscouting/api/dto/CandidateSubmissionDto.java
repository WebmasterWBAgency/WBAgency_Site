package com.wbscouting.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateSubmissionDto {

    private UUID id;
    private String protocol;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private Integer age;
    private Boolean isMinor;
    private String guardianName;
    private String guardianPhone;
    private String guardianEmail;
    private String gender;
    private String city;
    private String state;
    private Integer heightCm;
    private BigDecimal weight;
    private Integer bustCm;
    private Integer waistCm;
    private Integer hipsCm;

    public Integer getHeight() { return heightCm; }
    public void setHeight(Integer height) { this.heightCm = height; }
    public Integer getBust() { return bustCm; }
    public void setBust(Integer bust) { this.bustCm = bust; }
    public Integer getWaist() { return waistCm; }
    public void setWaist(Integer waist) { this.waistCm = waist; }
    public Integer getHips() { return hipsCm; }
    public void setHips(Integer hips) { this.hipsCm = hips; }
    private String shoeSize;
    private String eyeColor;
    private String hairColor;
    private String instagramHandle;
    private String tiktokHandle;
    private String status;
    private String feedbackNotes;
    private Boolean lgpdConsent;
    private OffsetDateTime lgpdConsentAt;
    private List<String> photoUrls;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
