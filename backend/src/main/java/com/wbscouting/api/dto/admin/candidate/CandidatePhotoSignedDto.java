package com.wbscouting.api.dto.admin.candidate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidatePhotoSignedDto {

    private UUID id;
    private Integer displayOrder;
    private String signedUrl;
    private Long expiresInSeconds;

    public CandidatePhotoSignedDto(UUID id, Integer displayOrder, String signedUrl) {
        this(id, displayOrder, signedUrl, 900L);
    }

    public CandidatePhotoSignedDto(UUID id, Integer displayOrder, String signedUrl, Integer expiresInSeconds) {
        this(id, displayOrder, signedUrl, expiresInSeconds != null ? expiresInSeconds.longValue() : 900L);
    }

    // Getter compatível com o template Angular legado que consome photo.url
    @JsonProperty("url")
    public String getUrl() {
        return this.signedUrl;
    }

    @JsonProperty("signedUrl")
    public String getSignedUrl() {
        return this.signedUrl;
    }
}
