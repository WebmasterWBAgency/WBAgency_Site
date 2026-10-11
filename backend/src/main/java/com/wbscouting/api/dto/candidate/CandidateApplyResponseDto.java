package com.wbscouting.api.dto.candidate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateApplyResponseDto {

    private UUID candidateId;
    private UUID id;
    private String protocol;
    private String message;
    private String status;
    private Instant submittedAt;

    public UUID getId() {
        return id != null ? id : candidateId;
    }
}
