package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contrato de serviço para submissão de formulários de candidatura com mídia.
 */
public interface CandidateSubmissionService {

    CandidateApplyResponseDto submit(CandidateApplyRequestDto requestDto, List<MultipartFile> photos);
}
