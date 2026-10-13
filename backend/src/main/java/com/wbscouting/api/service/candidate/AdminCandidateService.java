package com.wbscouting.api.service.candidate;

import com.wbscouting.api.dto.admin.candidate.CandidateDetailAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidateListItemAdminDto;
import com.wbscouting.api.dto.admin.candidate.CandidateNotesUpdateDto;
import com.wbscouting.api.dto.admin.candidate.CandidateStatusUpdateDto;
import com.wbscouting.api.enums.CandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@SuppressWarnings("deprecation")
public interface AdminCandidateService {

    /**
     * Lista candidaturas de forma paginada com suporte a filtros dinâmicos.
     * Projeção simplificada sem geração de signed URLs na listagem massiva.
     */
    Page<CandidateListItemAdminDto> listCandidates(
            CandidateStatus status,
            String search,
            String gender,
            Boolean isMinor,
            Pageable pageable
    );

    /**
     * Retorna os detalhes completos de uma candidatura com as respectivas fotos
     * acompanhadas de URLs assinadas temporárias (Signed URLs) para visualização.
     */
    CandidateDetailAdminDto getCandidateDetail(UUID id);

    /**
     * Atualiza o status de triagem da candidatura.
     */
    CandidateDetailAdminDto updateStatus(UUID id, CandidateStatusUpdateDto dto);

    /**
     * Atualiza as anotações internas da agência sobre o candidato.
     */
    CandidateDetailAdminDto updateNotes(UUID id, CandidateNotesUpdateDto dto);

    /**
     * Realiza o arquivamento (soft delete) da candidatura, alterando seu status para ARCHIVED
     * e registrando archivedAt.
     */
    void deleteCandidate(UUID id);

    /**
     * Realiza a exclusão da candidatura.
     * Se permanent == false, realiza soft delete (arquivamento).
     * Se permanent == true, realiza expurgo físico definitivo de fotos no storage e no banco.
     */
    void deleteCandidate(UUID id, boolean permanent);

    /**
     * Restaura uma candidatura do arquivo morto, retornando-a para o status PENDING
     * e limpando a flag archivedAt.
     */
    void restoreCandidate(UUID id);

    /**
     * Promove o candidato para o catálogo oficial de modelos.
     * Copia os dados do Candidate para Model, migra as fotos para ModelMedia,
     * remove fisicamente o registro original de candidates e candidate_photos,
     * e retorna o UUID do novo modelo criado.
     */
    UUID promoteToModel(UUID id, Boolean activateImmediately);

    /**
     * Realiza o expurgo de candidaturas e modelos no arquivo morto,
     * excluindo fotos físicas no Supabase Storage e registros no banco de dados.
     *
     * @param allArchived se true, expurga todas as submissões arquivadas/declinadas;
     *                    se false, apenas as arquivadas há mais de 7 dias.
     * @return estatísticas com purgedCount, message e nextScheduledPurge.
     */
    java.util.Map<String, Object> purgeArchive(boolean allArchived);

    /**
     * Invoca a limpeza de candidaturas arquivadas/declinadas há mais de 7 dias
     * (ou limpa todas as que estão em status DECLINED/REJECTED/ARCHIVED),
     * removendo fotos físicas no Supabase Storage e registros no banco de dados.
     *
     * @return quantidade total de registros expurgados.
     */
    int purgeArchivedSubmissions();
}
