package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.admin.candidate.CandidateApplicationSummaryDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDecisionRequestDto;
import com.wbscouting.api.dto.admin.candidate.CandidateDetailResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface CandidateSubmissionAdminService {

    Page<CandidateApplicationSummaryDto> listApplications(
            String status,
            String search,
            String gender,
            Boolean isMinor,
            Pageable pageable
    );

    Map<String, Long> getCounts();

    CandidateDetailResponseDto getApplicationById(UUID id);

    CandidateDetailResponseDto updateDecision(UUID id, CandidateDecisionRequestDto decisionDto);

    CandidateDetailResponseDto restoreApplication(UUID id);

    UUID promoteToModel(UUID id, Boolean activateImmediately);
}
