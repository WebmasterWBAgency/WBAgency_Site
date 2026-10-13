package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Implementação de serviço de submissão de candidaturas.
 * Provê ponto de entrada coeso com suporte a rollback compensatório no Supabase Storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateSubmissionServiceImpl implements CandidateSubmissionService {

    private final CandidateApplicationService candidateApplicationService;

    @Override
    public CandidateApplyResponseDto submit(CandidateApplyRequestDto requestDto, List<MultipartFile> photos) {
        log.info("[CandidateSubmissionServiceImpl] Processando submissão de candidatura de: {}",
                requestDto != null ? requestDto.getFullName() : "null");
        return candidateApplicationService.apply(requestDto, photos);
    }
}
