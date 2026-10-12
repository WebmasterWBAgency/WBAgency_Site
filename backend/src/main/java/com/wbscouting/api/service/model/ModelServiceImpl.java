package com.wbscouting.api.service.model;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.ModelDTO;
import com.wbscouting.api.dto.model.*;
import com.wbscouting.api.entity.Model;
import com.wbscouting.api.entity.ModelMedia;
import com.wbscouting.api.enums.GenderType;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.ModelMediaRepository;
import com.wbscouting.api.repository.ModelRepository;
import com.wbscouting.api.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.wbscouting.api.dto.media.MediaUploadResponseDto;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ModelRepository modelRepository;
    private final ModelMediaRepository modelMediaRepository;
    private final StorageService storageService;
    private final SupabaseProperties supabaseProperties;

    /**
     * JDBC Template com SQL PURO para diagnostico de conexao/dados.
     * required=false: injecao falha silenciosamente em ambientes sem DataSource
     * (ex: testes unitarios leves). Null-safe check em cada uso.
     */
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public ModelAdminResponseDto createModel(ModelCreateRequestDto dto) {
        log.info("Cadastrando novo modelo artístico: {}", dto.getStageName());

        com.wbscouting.api.enums.TalentLifecycleStatus initialStatus = Boolean.TRUE.equals(dto.getIsStar())
                ? com.wbscouting.api.enums.TalentLifecycleStatus.CAST_STAR
                : com.wbscouting.api.enums.TalentLifecycleStatus.CAST_PUBLIC;

        Model model = Model.builder()
                .stageName(dto.getStageName())
                .gender(dto.getGender())
                .status(initialStatus)
                .isStar(Boolean.TRUE.equals(dto.getIsStar()))
                .isFeaturedHome(Boolean.TRUE.equals(dto.getIsFeaturedHome()))
                .featuredOrder(dto.getFeaturedOrder())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .primaryPhotoUrl(dto.getPrimaryPhotoUrl())
                .instagramUrl(dto.getInstagramUrl())
                .birthDate(dto.getBirthDate())
                .heightCm(dto.getHeightCm())
                .city(dto.getCity())
                .nationality(dto.getNationality())
                .dressSize(dto.getDressSize())
                .shoeSize(dto.getShoeSize())
                .bustChestCm(dto.getBustChestCm())
                .waistCm(dto.getWaistCm())
                .hipsCm(dto.getHipsCm())
                .hairColor(dto.getHairColor())
                .eyesColor(dto.getEyesColor())
                .build();

        Model savedModel = modelRepository.save(model);
        log.info("Modelo cadastrado com sucesso com ID: {}", savedModel.getId());
        return mapToAdminResponse(savedModel);
    }

    @Override
    @Transactional
    public ModelAdminResponseDto updateModel(UUID id, ModelUpdateRequestDto dto) {
        log.info("Atualizando dados do modelo com ID: {}", id);

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        model.setStageName(dto.getStageName());
        model.setGender(dto.getGender());
        if (dto.getIsStar() != null) model.setIsStar(dto.getIsStar());
        if (dto.getIsFeaturedHome() != null) model.setIsFeaturedHome(dto.getIsFeaturedHome());
        model.setFeaturedOrder(dto.getFeaturedOrder());
        if (dto.getIsActive() != null) model.setIsActive(dto.getIsActive());
        model.setPrimaryPhotoUrl(dto.getPrimaryPhotoUrl());
        model.setInstagramUrl(dto.getInstagramUrl());
        model.setBirthDate(dto.getBirthDate());
        model.setHeightCm(dto.getHeightCm());
        model.setCity(dto.getCity());
        model.setNationality(dto.getNationality());
        model.setDressSize(dto.getDressSize());
        model.setShoeSize(dto.getShoeSize());
        model.setBustChestCm(dto.getBustChestCm());
        model.setWaistCm(dto.getWaistCm());
        model.setHipsCm(dto.getHipsCm());
        model.setHairColor(dto.getHairColor());
        model.setEyesColor(dto.getEyesColor());

        Model updatedModel = modelRepository.save(model);
        log.info("Modelo ID: {} atualizado com sucesso", updatedModel.getId());
        return mapToAdminResponse(updatedModel);
    }

    @Override
    @Transactional
    public ModelAdminResponseDto updateStatus(UUID id, ModelStatusPatchDto dto) {
        log.info("Atualizando status de ativação do modelo ID: {} para {}", id, dto.getIsActive());

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        model.setIsActive(dto.getIsActive());
        Model saved = modelRepository.save(model);
        return mapToAdminResponse(saved);
    }

    @Override
    @Transactional
    public ModelAdminResponseDto updateTalentStatus(UUID id, com.wbscouting.api.dto.talent.TalentStatusPatchDto dto) {
        log.info("Atualizando status do talento ID: {} para {}", id, dto.getStatus());

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Talento / Modelo", "id", id));

        model.setStatus(dto.getStatus());
        if (dto.getStatus() == com.wbscouting.api.enums.TalentLifecycleStatus.CAST_STAR) {
            model.setIsStar(true);
            model.setIsActive(true);
        } else if (dto.getStatus() == com.wbscouting.api.enums.TalentLifecycleStatus.CAST_PUBLIC
                || dto.getStatus() == com.wbscouting.api.enums.TalentLifecycleStatus.CAST_INTERNAL) {
            model.setIsActive(true);
        } else if (dto.getStatus() == com.wbscouting.api.enums.TalentLifecycleStatus.ARCHIVED
                || dto.getStatus() == com.wbscouting.api.enums.TalentLifecycleStatus.APPLICANT_DECLINED) {
            model.setIsActive(false);
        }

        if (StringUtils.hasText(dto.getInternalNotes())) {
            model.setInternalNotes(dto.getInternalNotes().trim());
        }

        Model saved = modelRepository.save(model);
        log.info("Status do talento ID: {} atualizado com sucesso para {}", id, saved.getStatus());
        return mapToAdminResponse(saved);
    }

    @Override
    @Transactional
    public ModelAdminResponseDto updateStar(UUID id, ModelStarPatchDto dto) {
        log.info("Atualizando status Star do modelo ID: {} para {}", id, dto.getIsStar());

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        model.setIsStar(dto.getIsStar());
        Model saved = modelRepository.save(model);
        return mapToAdminResponse(saved);
    }

    @Override
    @Transactional
    public ModelAdminResponseDto updateFeatured(UUID id, ModelFeaturedPatchDto dto) {
        log.info("Atualizando destaque na home do modelo ID: {} (isFeaturedHome={}, featuredOrder={})",
                id, dto.getIsFeaturedHome(), dto.getFeaturedOrder());

        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        model.setIsFeaturedHome(dto.getIsFeaturedHome());
        model.setFeaturedOrder(dto.getFeaturedOrder());
        Model saved = modelRepository.save(model);
        return mapToAdminResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelAdminResponseDto> listAdminModels(
            GenderType gender, Boolean isStar, Boolean isActive, String search, Pageable pageable) {
        return listAdminModels(gender, isStar, isActive, false, search, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelAdminResponseDto> listAdminModels(
            GenderType gender, Boolean isStar, Boolean isActive, Boolean archived, String search, Pageable pageable) {

        final String sanitizedSearch = (search == null || search.isBlank() || "null".equalsIgnoreCase(search.trim()) || "undefined".equalsIgnoreCase(search.trim()))
                ? null
                : search.trim();

        final boolean isArchived = Boolean.TRUE.equals(archived);

        final boolean hasFilters = (gender != null)
                || (isStar != null)
                || (isActive != null)
                || (sanitizedSearch != null);

        log.info("[ADMIN MODEL] listAdminModels → gender={}, isStar={}, isActive={}, archived={}, sanitizedSearch='{}', " +
                        "hasFilters={}, pageable.page={}, pageable.size={}, pageable.sort={}",
                gender, isStar, isActive, isArchived, sanitizedSearch,
                hasFilters,
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        Page<Model> pageResult;

        if (isArchived) {
            log.info("[ADMIN MODEL] Executando consulta de Arquivo Morto (archived=true).");
            org.springframework.data.jpa.domain.Specification<Model> spec =
                    com.wbscouting.api.repository.specification.ModelSpecification.filter(gender, isStar, isActive, true, sanitizedSearch);
            pageResult = modelRepository.findAll(spec, pageable);
            if (pageResult == null || pageResult.isEmpty()) {
                pageResult = modelRepository.findByArchivedAtIsNotNull(pageable);
            }
        } else if (!hasFilters) {
            log.info("[ADMIN MODEL] Executando findAllAdminPure() para carregar todos os modelos do Admin sem filtros restritivos.");
            pageResult = modelRepository.findAllAdminPure(pageable);
            if (pageResult == null || pageResult.isEmpty()) {
                pageResult = modelRepository.findByArchivedAtIsNull(pageable);
            }
        } else {
            log.info("[ADMIN MODEL] Executando consulta com Specification: gender={}, isStar={}, isActive={}, search='{}'",
                    gender, isStar, isActive, sanitizedSearch);
            org.springframework.data.jpa.domain.Specification<Model> spec =
                    com.wbscouting.api.repository.specification.ModelSpecification.filter(gender, isStar, isActive, false, sanitizedSearch);
            pageResult = modelRepository.findAll(spec, pageable);
            if (pageResult == null || pageResult.isEmpty()) {
                try {
                    pageResult = modelRepository.findAdminWithFilters(gender, isStar, isActive, sanitizedSearch, pageable);
                } catch (Exception ignored) {}
            }
        }

        if (pageResult == null) {
            pageResult = org.springframework.data.domain.Page.empty(pageable);
        }

        log.info("[ADMIN MODEL] Total retornado: {}", pageResult.getTotalElements());

        return pageResult.map(this::mapToAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelAdminResponseDto> listAdminModels(
            GenderType gender, Boolean isStar, Boolean isActive, List<com.wbscouting.api.enums.TalentLifecycleStatus> statuses, String search, Pageable pageable) {
        return listAdminModels(gender, isStar, isActive, false, search, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ModelAdminResponseDto getAdminModelById(UUID id) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));
        return mapToAdminResponse(model);
    }

    @Override
    @Transactional
    public void deleteModel(UUID id) {
        deleteModel(id, false);
    }

    @Override
    @Transactional
    public void deleteModel(UUID id, boolean permanent) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        if (!permanent) {
            log.info("Movendo modelo ID: {} ({}) para Arquivo Morto (soft delete)", id, model.getStageName());
            model.setIsActive(false);
            model.setArchivedAt(OffsetDateTime.now());
            modelRepository.save(model);
            return;
        }

        log.info("Excluindo permanentemente modelo ID: {} ({}) (Hard Delete)", id, model.getStageName());

        // 1. Buscar TODAS as midias vinculadas ao modelo (ativas e inativas)
        List<ModelMedia> allMedia = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(id);
        String bucket = supabaseProperties.resolveBucketModelsMedia();

        // 2. Remover arquivos FISICOS do Supabase Storage (FAIL-SAFE)
        int deletedFiles = 0;
        int failedFiles = 0;
        for (ModelMedia media : allMedia) {
            String filePath = media.getFilePath();
            if (!StringUtils.hasText(filePath)) {
                log.warn("Midia ID: {} do modelo ID: {} sem filePath valido. Pulando remocao do storage.", media.getId(), id);
                continue;
            }
            try {
                String targetBucket = (filePath.contains("candidates-uploads") || (media.getFileUrl() != null && media.getFileUrl().contains("candidates-uploads")))
                        ? "candidates-uploads"
                        : bucket;
                storageService.deleteFile(targetBucket, filePath);
                deletedFiles++;
            } catch (Exception e) {
                failedFiles++;
                log.warn("Falha NAO-CRITICA ao remover arquivo do storage. Midia ID: {}, path: {}, motivo: {}. Procedendo com exclusao do banco.",
                        media.getId(), filePath, e.getMessage());
            }
        }

        log.info("Remocao de arquivos do storage concluida. Modelo ID: {} -> {} removidos, {} falhas (nao criticas).", id, deletedFiles, failedFiles);

        // 3. Deletar as midias do banco SEM depender do cascade (evita FK constraint em alguns bancos)
        if (!allMedia.isEmpty()) {
            modelMediaRepository.deleteAll(allMedia);
            modelMediaRepository.flush();
            log.info("{} registros de midia removidos da tabela model_media para o modelo ID: {}", allMedia.size(), id);
        }

        // 4. Excluir definitivamente o modelo do banco
        modelRepository.delete(model);
        modelRepository.flush();

        log.info("Modelo ID: {} excluido com sucesso do banco de dados (Hard Delete).", id);
    }

    @Override
    @Transactional
    public ModelAdminResponseDto restoreModel(UUID id) {
        log.info("Restaurando modelo ID: {} do Arquivo Morto.", id);
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        model.setArchivedAt(null);
        model.setIsActive(false); // Para que o admin revise antes de reativar publicamente
        Model saved = modelRepository.save(model);
        log.info("Modelo ID: {} restaurado com sucesso. Status ativo definido como false para revisão.", id);
        return mapToAdminResponse(saved);
    }

    // Métodos Públicos de Catálogo (Portal / Site Institucional)

    @Override
    @Transactional(readOnly = true)
    public Page<ModelDTO.SummaryResponse> listModels(GenderType gender, Boolean isStar, Pageable pageable) {
        Page<Model> models;

        if (gender != null) {
            models = modelRepository.findByGenderAndIsActiveTrue(gender, pageable);
        } else if (Boolean.TRUE.equals(isStar)) {
            models = modelRepository.findByIsStarTrueAndIsActiveTrue(pageable);
        } else {
            models = modelRepository.findByIsActiveTrue(pageable);
        }

        return models.map(this::mapToSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelDTO.SummaryResponse> getFeaturedHomeModels() {
        return modelRepository.findFeaturedHomeModels()
                .stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ModelDTO.DetailResponse getModelById(UUID id) {
        Model model = modelRepository.findById(id)
                .filter(Model::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo", "id", id));

        return mapToDetail(model);
    }

    private ModelAdminResponseDto mapToAdminResponse(Model model) {
        List<ModelMedia> medias = null;
        if (model.getId() != null) {
            try {
                medias = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(model.getId());
            } catch (Exception ignored) {}
        }
        if ((medias == null || medias.isEmpty())) {
            try {
                if (model.getMedia() != null && !model.getMedia().isEmpty()) {
                    medias = model.getMedia();
                }
            } catch (Exception ignored) {
                // Previne LazyInitializationException
            }
        }

        final boolean isInactive = !Boolean.TRUE.equals(model.getIsActive());

        List<MediaUploadResponseDto> mediaDtos = new ArrayList<>();
        if (medias != null && !medias.isEmpty()) {
            String bucket = supabaseProperties.resolveBucketModelsMedia();
            mediaDtos = medias.stream()
                    .map(m -> {
                        String rawUrl = m.getFileUrl();
                        String filePath = m.getFilePath();
                        String safeUrl;
                        boolean isCandidateUpload = (filePath != null && filePath.contains("candidates-uploads"))
                                || (rawUrl != null && rawUrl.contains("candidates-uploads"));

                        if (isCandidateUpload) {
                            // Regra Estrita de Negócio: candidates-uploads é bucket privado (LGPD) -> SEMPRE Signed URL de 3600s
                            String targetPath = StringUtils.hasText(filePath) ? filePath : rawUrl;
                            String signed = storageService.generateSignedUrl("candidates-uploads", targetPath, 3600);
                            safeUrl = (signed != null) ? signed : storageService.resolvePublicUrlFromFields(bucket, filePath, rawUrl);
                        } else if (isInactive) {
                            // Modelo Inativo: fotos privadas servidas exclusivamente via Signed URLs temporárias (3600s / 1 hora)
                            String targetPath = StringUtils.hasText(filePath) ? filePath : rawUrl;
                            String signed = storageService.generateSignedUrl(bucket, targetPath, 3600);
                            safeUrl = (signed != null) ? signed : storageService.resolvePublicUrlFromFields(bucket, filePath, rawUrl);
                        } else {
                            // Modelo Ativo com mídia no bucket público models-media: foto pública direta
                            safeUrl = storageService.resolvePublicUrlFromFields(bucket, filePath, rawUrl);
                            if (safeUrl != null && !safeUrl.isBlank() && !safeUrl.contains("?v=")) {
                                safeUrl = safeUrl + "?v=" + (System.currentTimeMillis() / 3_600_000L);
                            }
                        }
                        return MediaUploadResponseDto.builder()
                                .id(m.getId())
                                .modelId(model.getId())
                                .mediaType(m.getMediaType())
                                .fileUrl(safeUrl)
                                .filePath(m.getFilePath())
                                .displayOrder(m.getDisplayOrder())
                                .isCover(m.getIsCover())
                                .isActive(m.getIsActive())
                                .createdAt(m.getCreatedAt())
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        String resolvedPrimaryUrl = resolveCoverPhotoUrl(model, medias);
        if (resolvedPrimaryUrl != null && !resolvedPrimaryUrl.isBlank()) {
            boolean isCoverCandidateUpload = resolvedPrimaryUrl.contains("candidates-uploads");
            if (isCoverCandidateUpload) {
                // Fotos originárias de candidaturas residem no bucket privado candidates-uploads (LGPD) -> SEMPRE Signed URL de 3600s
                String signedCover = storageService.generateSignedUrl("candidates-uploads", resolvedPrimaryUrl, 3600);
                if (signedCover != null) {
                    resolvedPrimaryUrl = signedCover;
                }
            } else if (isInactive) {
                // Modelo Inativo com capa em models-media: Signed URL 3600s
                String defaultBucket = supabaseProperties.resolveBucketModelsMedia();
                String signedCover = storageService.generateSignedUrl(defaultBucket, resolvedPrimaryUrl, 3600);
                if (signedCover != null) {
                    resolvedPrimaryUrl = signedCover;
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
                .email(model.getEmail())
                .phone(model.getPhone())
                .age(model.getAge())
                .state(model.getState())
                .weightKg(model.getWeightKg())
                .legalGuardianName(model.getLegalGuardianName())
                .legalGuardianContact(model.getLegalGuardianContact())
                .lgpdAccepted(model.getLgpdAccepted())
                .internalNotes(model.getInternalNotes())
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
                .media(mediaDtos)
                .build();
    }

    private ModelDTO.SummaryResponse mapToSummary(Model model) {
        List<ModelMedia> medias = null;
        try {
            if (model.getMedia() != null && !model.getMedia().isEmpty()) {
                medias = model.getMedia();
            }
        } catch (Exception ignored) {}
        if (medias == null && model.getId() != null) {
            try {
                medias = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(model.getId());
            } catch (Exception ignored) {}
        }

        String resolvedPrimaryUrl = resolveCoverPhotoUrl(model, medias);

        return ModelDTO.SummaryResponse.builder()
                .id(model.getId())
                .stageName(model.getStageName())
                .gender(model.getGender())
                .isStar(model.getIsStar())
                .primaryPhotoUrl(resolvedPrimaryUrl)
                .coverPhotoUrl(resolvedPrimaryUrl)
                .heightCm(model.getHeightCm())
                .dressSize(model.getDressSize())
                .shoeSize(model.getShoeSize())
                .city(model.getCity())
                .build();
    }

    private ModelDTO.DetailResponse mapToDetail(Model model) {
        List<ModelMedia> medias = null;
        try {
            if (model.getMedia() != null && !model.getMedia().isEmpty()) {
                medias = model.getMedia();
            }
        } catch (Exception ignored) {}
        if (medias == null && model.getId() != null) {
            try {
                medias = modelMediaRepository.findByModelIdOrderByDisplayOrderAsc(model.getId());
            } catch (Exception ignored) {}
        }

        List<ModelDTO.MediaResponse> mediaResponses = new ArrayList<>();
        if (medias != null && !medias.isEmpty()) {
            mediaResponses = medias.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                    .map(m -> ModelDTO.MediaResponse.builder()
                            .id(m.getId())
                            .mediaType(m.getMediaType())
                            .fileUrl(m.getFileUrl())
                            .displayOrder(m.getDisplayOrder())
                            .build())
                    .collect(Collectors.toList());
        }

        String resolvedPrimaryUrl = resolveCoverPhotoUrl(model, medias);

        return ModelDTO.DetailResponse.builder()
                .id(model.getId())
                .stageName(model.getStageName())
                .gender(model.getGender())
                .isStar(model.getIsStar())
                .isFeaturedHome(model.getIsFeaturedHome())
                .primaryPhotoUrl(resolvedPrimaryUrl)
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
                .media(mediaResponses)
                .build();
    }

    private String resolveCoverPhotoUrl(Model model, List<ModelMedia> medias) {
        String resolved = null;
        if (medias != null && !medias.isEmpty()) {
            // 1. Localiza a imagem associada onde isCover == true e isActive == true
            resolved = medias.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsCover()) && Boolean.TRUE.equals(m.getIsActive()))
                    .map(m -> StringUtils.hasText(m.getFileUrl()) ? m.getFileUrl().trim() : (m.getFilePath() != null ? m.getFilePath().trim() : null))
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);

            // Se nenhuma foto ativa tiver isCover == true, tenta isCover == true sem checagem estrita de isActive
            if (!StringUtils.hasText(resolved)) {
                resolved = medias.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsCover()))
                        .map(m -> StringUtils.hasText(m.getFileUrl()) ? m.getFileUrl().trim() : (m.getFilePath() != null ? m.getFilePath().trim() : null))
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null);
            }

            // 2. Fallback obrigatório: se nenhuma foto estiver com isCover = true, utilize a URL da primeira mídia ativa
            if (!StringUtils.hasText(resolved)) {
                resolved = medias.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                        .map(m -> StringUtils.hasText(m.getFileUrl()) ? m.getFileUrl().trim() : (m.getFilePath() != null ? m.getFilePath().trim() : null))
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null);
            }

            // 3. Fallback absoluto para a primeira mídia disponível da lista (garantindo que o card nunca fique sem imagem)
            if (!StringUtils.hasText(resolved)) {
                ModelMedia first = medias.get(0);
                resolved = StringUtils.hasText(first.getFileUrl()) ? first.getFileUrl().trim() : (first.getFilePath() != null ? first.getFilePath().trim() : null);
            }
        }

        // Se ainda não encontrou nas mídias filhas, recorre à coluna do modelo
        if (!StringUtils.hasText(resolved) && StringUtils.hasText(model.getPrimaryPhotoUrl())) {
            resolved = model.getPrimaryPhotoUrl().trim();
        }

        return resolved;
    }
}

