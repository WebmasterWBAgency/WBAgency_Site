package com.wbscouting.api.scheduler;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.entity.CandidatePhoto;
import com.wbscouting.api.entity.CandidateSubmission;
import com.wbscouting.api.repository.CandidatePhotoRepository;
import com.wbscouting.api.repository.CandidateSubmissionRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveCleanupScheduler {

    private final CandidateSubmissionRepository submissionRepository;
    private final CandidatePhotoRepository photoRepository;
    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final StorageService storageService;
    private final SupabaseProperties supabaseProperties;

    // Expurgo manual sob demanda (expurgo programado/automático de candidaturas desativado)
    @Transactional
    public void purgeArchivedRecordsOlderThan7Days() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(7);
        log.info("[ARCHIVE-CLEANUP] Executando expurgo sob demanda de registros arquivados antes de: {}", threshold);

        // 1. Busca registros que estão no arquivo morto há mais de 7 dias
        List<CandidateSubmission> expired = submissionRepository.findByArchivedAtBefore(threshold);

        for (CandidateSubmission sub : expired) {
            try {
                // 1.1 Apaga arquivos físicos do Storage
                List<CandidatePhoto> photos = photoRepository.findByCandidateId(sub.getId());
                for (CandidatePhoto p : photos) {
                    if (p.getFilePath() != null && !p.getFilePath().isBlank()) {
                        try {
                            storageService.deleteFile("candidates-uploads", p.getFilePath());
                        } catch (Exception ex) {
                            try {
                                storageService.deleteFile(p.getFilePath());
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // 1.2 Apaga do banco de dados (cascade limpa candidate_photos)
                submissionRepository.delete(sub);
                log.info("[ARCHIVE-CLEANUP] Registro expurgado: protocol={}, id={}", sub.getProtocol(), sub.getId());
            } catch (Exception ex) {
                log.error("[ARCHIVE-CLEANUP] Falha ao expurgar submissao {}: {}", sub.getId(), ex.getMessage());
            }
        }

        // 2. Busca e expurga modelos candidatos arquivados há mais de 7 dias
        List<Model> expiredModels = modelRepository.findByStatusArchivedBefore(threshold);
        String bucket = (supabaseProperties != null) ? supabaseProperties.resolveBucketCandidates() : "candidates-uploads";
        for (Model m : expiredModels) {
            try {
                if (m.getMedia() != null) {
                    for (ModelMedia media : m.getMedia()) {
                        String p = media.getFilePath() != null ? media.getFilePath() : media.getFileUrl();
                        if (p != null && !p.isBlank()) {
                            try {
                                storageService.deleteFile("candidates-uploads", p);
                            } catch (Exception ex) {
                                try {
                                    storageService.deleteFile(bucket, p);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    modelMediaRepository.deleteAll(m.getMedia());
                }
                modelRepository.delete(m);
                log.info("[ARCHIVE-CLEANUP] Modelo candidato expurgado: id={}", m.getId());
            } catch (Exception ex) {
                log.error("[ARCHIVE-CLEANUP] Falha ao expurgar modelo {}: {}", m.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Expurgador Automático (30 dias) de Modelos no Arquivo Morto.
     * Executa diariamente às 03:00 da manhã.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeArchivedModelsOlderThan30Days() {
        OffsetDateTime limitDate = OffsetDateTime.now().minusDays(30);
        log.info("[CLEANUP-30-DIAS] Verificando modelos arquivados antes de: {}", limitDate);

        List<Model> expiredModels = modelRepository.findByArchivedAtBefore(limitDate);
        String bucket = (supabaseProperties != null) ? supabaseProperties.resolveBucketModelsMedia() : "models-media";

        for (Model model : expiredModels) {
            try {
                if (model.getMedia() != null) {
                    for (ModelMedia media : model.getMedia()) {
                        String path = media.getFilePath() != null && !media.getFilePath().isBlank()
                                ? media.getFilePath()
                                : media.getFileUrl();
                        if (path != null && !path.isBlank()) {
                            try {
                                storageService.deleteFile(bucket, path);
                            } catch (Exception ex) {
                                try {
                                    storageService.deleteFile(path);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    modelMediaRepository.deleteAll(model.getMedia());
                }
                modelRepository.delete(model);
                log.info("[CLEANUP-30-DIAS] Modelo expurgado com sucesso: ID {} ({})", model.getId(), model.getStageName());
            } catch (Exception ex) {
                log.error("[CLEANUP-30-DIAS] Erro ao expurgar modelo ID {}: {}", model.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Expurgador Automático (30 dias) de Modelos no Arquivo Morto.
     * Executa diariamente às 03:00 da manhã.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeExpiredArchivedModels() {
        purgeArchivedModelsOlderThan30Days();
    }

    // Alias compatível com chamadas legadas
    public void cleanupArchivedRecordsOlderThan7Days() {
        purgeArchivedRecordsOlderThan7Days();
    }
}
