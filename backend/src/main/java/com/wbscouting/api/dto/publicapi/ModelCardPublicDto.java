package com.wbscouting.api.dto.publicapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.wbscouting.api.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelCardPublicDto {
    private UUID id;
    private String stageName;
    private GenderType gender;
    private String coverImageUrl;
    private String coverPhotoUrl;
    private String primaryPhotoUrl;
    private Integer heightCm;

    @JsonIgnore
    private String city;

    private Boolean isStar;

    public String getCoverImageUrl() {
        if (coverImageUrl != null) return coverImageUrl;
        if (coverPhotoUrl != null) return coverPhotoUrl;
        return primaryPhotoUrl;
    }

    public String getCoverPhotoUrl() {
        if (coverPhotoUrl != null) return coverPhotoUrl;
        if (coverImageUrl != null) return coverImageUrl;
        return primaryPhotoUrl;
    }

    public String getPrimaryPhotoUrl() {
        if (primaryPhotoUrl != null) return primaryPhotoUrl;
        if (coverPhotoUrl != null) return coverPhotoUrl;
        return coverImageUrl;
    }
}
