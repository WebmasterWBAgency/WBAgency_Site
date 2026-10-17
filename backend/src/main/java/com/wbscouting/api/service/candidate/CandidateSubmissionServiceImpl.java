package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.candidate.CandidateApplyRequestDto;
import com.wbscouting.api.dto.candidate.CandidateApplyResponseDto;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
    private final CandidateSubmissionRepository candidateSubmissionRepository;
    private final StorageService storageService;

    @Override
    public CandidateApplyResponseDto submit(CandidateApplyRequestDto requestDto, List<MultipartFile> photos) {
        return submitCandidate(requestDto, photos);
    }

    @Override
    public CandidateApplyResponseDto submitCandidate(CandidateApplyRequestDto requestDto, List<MultipartFile> photos) {
        log.info("[CandidateSubmissionServiceImpl] Processando submissão de candidatura de: {}",
                requestDto != null ? requestDto.getFullName() : "null");

        List<String> uploadedPaths = new ArrayList<>();
        try {
            return candidateApplicationService.apply(requestDto, photos);
        } catch (Exception ex) {
            log.error("Falha ao persistir candidatura no banco de dados. Iniciando expurgo compensatório de arquivos no Storage: {}", ex.getMessage());
            for (String storagePath : uploadedPaths) {
                try {
                    storageService.deleteFile("candidates-uploads", storagePath);
                } catch (Exception deleteEx) {
                    log.warn("Falha ao remover arquivo órfão {}: {}", storagePath, deleteEx.getMessage());
                }
            }
            throw ex;
        }
    }
}
