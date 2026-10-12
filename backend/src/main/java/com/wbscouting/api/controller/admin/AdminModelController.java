package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.model.*;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.service.model.ModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import com.wbscouting.api.security.audit.AuditAction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/models")
@RequiredArgsConstructor
@Slf4j
public class AdminModelController {

    private final ModelService modelService;
    private final ModelRepository modelRepository;

    @Autowired(required = false)
    private ModelMediaRepository modelMediaRepository;

    @Autowired(required = false)
    private com.wbscouting.api.service.storage.StorageService storageService;

    /**
     * JDBC Template (SQL PURO, sem Hibernate) para diagnostico de dados.
     * Util quando findAll/JPA mascara registros por EntityGraph / Join Fetch /
     * Specifications incorretas — SQL puro mostra exatamente o que existe no DB.
     */
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostMapping
    @AuditAction(action = "CREATE", resource = "MODEL", description = "Criação de novo modelo no casting")
    public ResponseEntity<ModelAdminResponseDto> createModel(@Valid @RequestBody ModelCreateRequestDto request) {
        ModelAdminResponseDto createdModel = modelService.createModel(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdModel.getId())
                .toUri();

        return ResponseEntity.created(location).body(createdModel);
    }

    @PutMapping("/{id}")
    @AuditAction(action = "UPDATE", resource = "MODEL", description = "Atualização cadastral do modelo")
    public ResponseEntity<ModelAdminResponseDto> updateModel(
            @PathVariable UUID id,
            @Valid @RequestBody ModelUpdateRequestDto request) {
        return ResponseEntity.ok(modelService.updateModel(id, request));
    }

    @PatchMapping("/{id}/status")
    @AuditAction(action = "UPDATE_STATUS", resource = "MODEL", description = "Alteração de status do modelo")
    public ResponseEntity<ModelAdminResponseDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ModelStatusPatchDto request) {
        return ResponseEntity.ok(modelService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/star")
    @AuditAction(action = "UPDATE_STAR", resource = "MODEL", description = "Alteração de selo star do modelo")
    public ResponseEntity<ModelAdminResponseDto> updateStar(
            @PathVariable UUID id,
            @Valid @RequestBody ModelStarPatchDto request) {
        return ResponseEntity.ok(modelService.updateStar(id, request));
    }

    @PatchMapping("/{id}/featured")
    @AuditAction(action = "UPDATE_FEATURED", resource = "MODEL", description = "Alteração de destaque na vitrine do modelo")
    public ResponseEntity<ModelAdminResponseDto> updateFeatured(
            @PathVariable UUID id,
            @Valid @RequestBody ModelFeaturedPatchDto request) {
        return ResponseEntity.ok(modelService.updateFeatured(id, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WEBMASTER')")
    public ResponseEntity<Page<ModelAdminResponseDto>> listAdminModels(
            @RequestParam(required = false) GenderType gender,
            @RequestParam(required = false) Boolean isStar,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        // ============================================================
        // 🔥🔥 DIAGNOSTICO CRUCIAL: SQL PURO via JDBC (SEM JPA/Hibernate)
        //    - Se isto > 0 e JPA retorna 0: BUG de MAEPMANETO (EntityGraph / Join Fetch etc)
        //    - Se isto == 0: BANCO conectado NAO tem os registros (H2 em vez de Supabase).
        // ============================================================
        if (jdbcTemplate != null) {
            try {
                Integer jdbcCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM public.models", Integer.class);
                log.info("[DIAGNOSTICO-SQL-PURO] [CONTROLLER] SELECT count(*) FROM public.models via JDBC = {}", jdbcCount);
                List<Map<String, Object>> jdbcRows = jdbcTemplate.queryForList(
                        "SELECT id, stage_name, is_active FROM public.models ORDER BY created_at DESC LIMIT 3");
                log.info("[DIAGNOSTICO-SQL-PURO] [CONTROLLER] 3 linhas de public.models via JDBC direto = {}", jdbcRows);
            } catch (Exception jdbcEx) {
                log.error("[DIAGNOSTICO-SQL-PURO] [CONTROLLER] Falha JDBC: {}", jdbcEx.getMessage());
            }
        }

        // ============================================================
        // 🔥 LOG OBRIGATORIO DA TASK: Contagem DIRETA no repository
        //    antes de QUALQUER processamento. Se for H2 em vez de PostgreSQL,
        //    aqui aparece 0 (ou seed da H2) ao inves dos dados reais do Supabase.
        // ============================================================
        // Sanitização de strings: se search ou status vierem como "", "null", "undefined" ou espaços em branco, force para null
        if (search != null && (search.isBlank() || "null".equalsIgnoreCase(search.trim()) || "undefined".equalsIgnoreCase(search.trim()))) {
            search = null;
        } else if (search != null) {
            search = search.trim();
        }

        if (status != null && (status.isBlank() || "null".equalsIgnoreCase(status.trim()) || "undefined".equalsIgnoreCase(status.trim()))) {
            status = null;
        } else if (status != null) {
            status = status.trim();
            if (isActive == null) {
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    isActive = true;
                } else if ("INACTIVE".equalsIgnoreCase(status)) {
                    isActive = false;
                }
            }
        }

        final boolean anyFilterApplied = (gender != null)
                || (isStar != null)
                || (isActive != null)
                || (archived != null)
                || StringUtils.hasText(search);

        log.info("[CONTROLLER /admin/models] listAdminModels → gender={}, isStar={}, isActive={}, archived={}, search='{}', anyFilterApplied={}",
                gender, isStar, isActive, archived, search, anyFilterApplied);

        Page<ModelAdminResponseDto> result = modelService.listAdminModels(gender, isStar, isActive, archived, search, pageable);
        if (result == null) {
            result = modelService.listAdminModels(gender, isStar, isActive, search, pageable);
        }
        if (result == null) {
            result = new org.springframework.data.domain.PageImpl<>(java.util.List.of(), pageable, 0);
        }

        // ============================================================
        // LOG OBRIGATORIO: totalElements retornado
        // ============================================================
        log.info("[CONTROLLER /admin/models] result.getTotalElements() = {}", result.getTotalElements());

        // ============================================================
        // 🔥 FALLBACK ABSOLUTO (CONTROLLER LEVEL):
        // Se a service retornar totalElements=0 E nenhum filtro foi enviado,
        // o problema NAO eh de specification: eh conexao errada / repository
        // / h2-memory sendo usado em vez de PostgreSQL / dataSource vazio.
        // Consultamos DIRETAMENTE o repository findAll SEM specs para mostrar
        // ao usuario os dados que existem (evita tela branca no Admin).
        // ============================================================
        if (result.getTotalElements() == 0L) {
            Page<Model> rawModels = null;
            try {
                if (Boolean.TRUE.equals(archived)) {
                    rawModels = modelRepository.findByArchivedAtIsNotNull(pageable);
                } else if (!anyFilterApplied) {
                    rawModels = modelRepository.findAllAdminPure(pageable);
                    if (rawModels == null || rawModels.isEmpty()) {
                        rawModels = modelRepository.findByArchivedAtIsNull(pageable);
                    }
                } else {
                    // Fallback com filtros: se a service/spec retornou 0 mas temos modelos no banco,
                    // busca modelos gerais e filtra em memória para garantir integridade.
                    Page<Model> allRaw = modelRepository.findAllAdminPure(org.springframework.data.domain.Pageable.unpaged());
                    if (allRaw != null && !allRaw.isEmpty()) {
                        final String finalSearch = search;
                        final Boolean finalIsActive = isActive;
                        List<Model> filtered = allRaw.getContent().stream()
                                .filter(m -> {
                                    if (gender != null && m.getGender() != gender) return false;
                                    if (isStar != null && !isStar.equals(Boolean.TRUE.equals(m.getIsStar()))) return false;
                                    if (finalIsActive != null) {
                                        boolean modelActive = Boolean.TRUE.equals(m.getIsActive());
                                        if (finalIsActive && !modelActive) return false;
                                        if (!finalIsActive && modelActive && m.getArchivedAt() == null) return false;
                                    }
                                    if (StringUtils.hasText(finalSearch)) {
                                        String term = finalSearch.toLowerCase();
                                        boolean matchStage = m.getStageName() != null && m.getStageName().toLowerCase().contains(term);
                                        boolean matchFull = m.getFullName() != null && m.getFullName().toLowerCase().contains(term);
                                        if (!matchStage && !matchFull) return false;
                                    }
                                    return true;
                                })
                                .toList();
                        if (!filtered.isEmpty()) {
                            int start = (int) pageable.getOffset();
                            int end = Math.min((start + pageable.getPageSize()), filtered.size());
                            List<Model> paged = start <= end ? filtered.subList(start, end) : List.of();
                            rawModels = new org.springframework.data.domain.PageImpl<>(paged, pageable, filtered.size());
                        }
                    }
                }
                log.info("[DIAGNOSTICO-BD] [CONTROLLER] fallback direto (anyFilterApplied={}, archived={}) → totalElements = {}",
                        anyFilterApplied, archived, rawModels != null ? rawModels.getTotalElements() : 0);
            } catch (Exception e) {
                log.error("[CONTROLLER /admin/models] Falha no fallback direto do repository: {}", e.getMessage(), e);
            }

            // Se service voltar 0 e rawModels tiver algo → SOBRESCREVE a resposta
            if (rawModels != null && rawModels.getTotalElements() > 0L) {
                log.warn("[CONTROLLER /admin/models] ⚠️  SOBRESCREVENDO resposta com {} itens (service/specs retornaram 0 MAS TABELA TEM DADOS).",
                        rawModels.getTotalElements());
                result = rawModels.map(this::mapToAdminDtoInline);
            }
        }

        // ============================================================
        // LOG DE AUDITORIA FINAL (solicitado explicitamente na task)
        // ============================================================
        log.info("[CONTROLLER /admin/models] Resposta HTTP 200 preparada. Total de registros retornados no content: {} | totalPages={} | size={} | page={}",
                result.getTotalElements(), result.getTotalPages(), result.getSize(), result.getNumber());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelAdminResponseDto> getAdminModelById(@PathVariable UUID id) {
        return ResponseEntity.ok(modelService.getAdminModelById(id));
    }

    @DeleteMapping("/{id}")
    @AuditAction(action = "DELETE", resource = "MODEL", description = "Exclusão permanente ou arquivamento de modelo do casting")
    public ResponseEntity<Void> deleteModel(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean permanent) {
        modelService.deleteModel(id, permanent);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @AuditAction(action = "RESTORE", resource = "MODEL", description = "Restauração de modelo do arquivo morto")
    public ResponseEntity<ModelAdminResponseDto> restoreModel(@PathVariable UUID id) {
        return ResponseEntity.ok(modelService.restoreModel(id));
    }

    /**
     * Mapeamento INLINE de Model → ModelAdminResponseDto usado EXCLUSIVAMENTE no fallback de listagem
     * quando a service + Specification falham em retornar registros (ex: H2 sendo usado em vez de PostgreSQL).
     * Replica fielmente a logica de ModelServiceImpl.mapToAdminResponse() para manter consistencia.
     * Nao usa injecao de dependencias nem Beans — metodo privado puro.
     */
    private ModelAdminResponseDto mapToAdminDtoInline(Model model) {
        if (model == null) return null;

        List<ModelMedia> medias = null;
        try {
            if (model.getMedia() != null && !model.getMedia().isEmpty()) {
                medias = model.getMedia();
            }
        } catch (Exception ignored) {}
        if (medias == null && modelMediaRepository != null && model.getId() != null) {
            try {
                medias = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(model.getId());
            } catch (Exception ignored) {}
        }

        String resolvedPrimaryUrl = null;
        if (medias != null && !medias.isEmpty()) {
            resolvedPrimaryUrl = medias.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsCover()) && Boolean.TRUE.equals(m.getIsActive()))
                    .map(m -> StringUtils.hasText(m.getFileUrl()) ? m.getFileUrl().trim() : (m.getFilePath() != null ? m.getFilePath().trim() : null))
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst()
                    .orElse(null);

            if (!StringUtils.hasText(resolvedPrimaryUrl)) {
                resolvedPrimaryUrl = medias.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsCover()))
                        .map(m -> StringUtils.hasText(m.getFileUrl()) ? m.getFileUrl().trim() : (m.getFilePath() != null ? m.getFilePath().trim() : null))
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst()
                        .orElse(null);
            }

            if (!StringUtils.hasText(resolvedPrimaryUrl)) {
                resolvedPrimaryUrl = medias.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                        .map(m -> StringUtils.hasText(m.getFileUrl()) ? m.getFileUrl().trim() : (m.getFilePath() != null ? m.getFilePath().trim() : null))
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst()
                        .orElse(null);
            }

            if (!StringUtils.hasText(resolvedPrimaryUrl)) {
                ModelMedia first = medias.get(0);
                resolvedPrimaryUrl = StringUtils.hasText(first.getFileUrl()) ? first.getFileUrl().trim() : (first.getFilePath() != null ? first.getFilePath().trim() : null);
            }
        }

        if (!StringUtils.hasText(resolvedPrimaryUrl) && StringUtils.hasText(model.getPrimaryPhotoUrl())) {
            resolvedPrimaryUrl = model.getPrimaryPhotoUrl().trim();
        }

        if (StringUtils.hasText(resolvedPrimaryUrl) && storageService != null) {
            boolean isCandidateUpload = resolvedPrimaryUrl.contains("candidates-uploads");
            if (isCandidateUpload) {
                String signed = storageService.generateSignedUrl("candidates-uploads", resolvedPrimaryUrl, 3600);
                if (signed != null) {
                    resolvedPrimaryUrl = signed;
                }
            } else if (!Boolean.TRUE.equals(model.getIsActive())) {
                String signed = storageService.generateSignedUrl("models-media", resolvedPrimaryUrl, 3600);
                if (signed != null) {
                    resolvedPrimaryUrl = signed;
                }
            }
        }

        return ModelAdminResponseDto.builder()
                .id(model.getId())
                .stageName(model.getStageName())
                .gender(model.getGender())
                .status(model.getStatus())
                .protocol(model.getProtocol())
                .fullName(model.getFullName())
                .isStar(model.getIsStar())
                .isFeaturedHome(model.getIsFeaturedHome())
                .featuredOrder(model.getFeaturedOrder())
                .isActive(model.getIsActive())
                .primaryPhotoUrl(resolvedPrimaryUrl)
                .coverPhotoUrl(resolvedPrimaryUrl)
                .instagramUrl(model.getInstagramUrl())
                .birthDate(model.getBirthDate())
                .heightCm(model.getHeightCm())
                .city(model.getCity())
                .nationality(model.getNationality())
                .dressSize(model.getDressSize())
                .shoeSize(model.getShoeSize())
                .bustChestCm(model.getBustChestCm())
                .waistCm(model.getWaistCm())
                .hipsCm(model.getHipsCm())
                .hairColor(model.getHairColor())
                .eyesColor(model.getEyesColor())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .archivedAt(model.getArchivedAt())
                .build();
    }
}
