package com.wbscouting.api.service.content;

import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.dto.content.AssetUploadResponseDto;
import com.wbscouting.api.dto.content.SiteContentAdminDto;
import com.wbscouting.api.dto.content.SiteContentPublicDto;
import com.wbscouting.api.dto.content.SiteContentUpdateRequestDto;
import com.wbscouting.api.entity.SiteContent;
import com.wbscouting.api.exception.FileSizeExceededException;
import com.wbscouting.api.exception.InvalidFileException;
import com.wbscouting.api.repository.SiteContentRepository;
import com.wbscouting.api.service.storage.StorageService;
import com.wbscouting.api.service.storage.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SiteContentServiceImpl implements SiteContentService {

    private final SiteContentRepository siteContentRepository;
    private final StorageService storageService;
    private final SupabaseProperties supabaseProperties;

    public SiteContentServiceImpl(SiteContentRepository siteContentRepository,
                                  StorageService storageService,
                                  SupabaseProperties supabaseProperties) {
        this.siteContentRepository = siteContentRepository;
        this.storageService = storageService;
        this.supabaseProperties = supabaseProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public SiteContentPublicDto getPublicContent(String sectionKey, String lang) {
        log.info("Consultando conteúdo público para sectionKey='{}', lang='{}'", sectionKey, lang);

        String upper = sectionKey != null ? sectionKey.trim().toUpperCase(Locale.ROOT) : "";
        String normalizedKey = switch (upper) {
            case "TERMS", "TERMS_OF_USE" -> "TERMS";
            case "PRIVACY", "PRIVACY_POLICY" -> "PRIVACY";
            case "ABOUT", "MANIFESTO" -> "ABOUT_MANIFESTO";
            default -> upper;
        };

        Optional<SiteContent> contentOpt = findSectionWithAlias(normalizedKey);
        String resolvedLang = resolveLanguage(lang);

        if (contentOpt.isEmpty()) {
            log.info("Seção '{}' não encontrada no banco. Retornando estrutura vazia com 200 OK.", sectionKey);
            return SiteContentPublicDto.builder()
                    .sectionKey(sectionKey != null ? sectionKey : "")
                    .payload(Collections.emptyMap())
                    .mediaUrls(Collections.emptyMap())
                    .lang(resolvedLang)
                    .content(Collections.emptyMap())
                    .build();
        }

        SiteContent content = contentOpt.get();
        Map<String, Object> resolvedPayload = resolvePayloadByLanguage(content, resolvedLang);

        Object contentObj;
        if (resolvedPayload != null && resolvedPayload.containsKey("content")) {
            contentObj = resolvedPayload.get("content");
        } else if (resolvedPayload != null && !resolvedPayload.isEmpty()) {
            contentObj = resolvedPayload;
        } else {
            contentObj = Collections.emptyMap();
        }

        return SiteContentPublicDto.builder()
                .sectionKey(content.getSectionKey())
                .payload(resolvedPayload != null ? resolvedPayload : Collections.emptyMap())
                .mediaUrls(content.getMediaUrls() != null ? content.getMediaUrls() : Collections.emptyMap())
                .lang(resolvedLang)
                .content(contentObj)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> getAllPublicContent(String lang) {
        String resolvedLang = resolveLanguage(lang);
        log.info("Consultando mapa consolidado de conteúdos públicos para lang='{}'", resolvedLang);

        List<SiteContent> contents = siteContentRepository.findAll();
        if (contents.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> consolidated = new LinkedHashMap<>();
        for (SiteContent content : contents) {
            Map<String, Object> payload = resolvePayloadByLanguage(content, resolvedLang);
            consolidated.put(content.getSectionKey(), payload != null ? payload : Collections.emptyMap());
        }

        return consolidated;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteContentAdminDto> getAllAdminContent() {
        log.info("Listando todas as seções e payloads de conteúdos para painel CMS");

        return siteContentRepository.findAll().stream()
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SiteContentAdminDto updateContent(String sectionKey, SiteContentUpdateRequestDto dto, UUID adminId) {
        String upper = sectionKey != null ? sectionKey.trim().toUpperCase(Locale.ROOT) : "";
        String normalizedKey = switch (upper) {
            case "TERMS", "TERMS_OF_USE" -> "TERMS";
            case "PRIVACY", "PRIVACY_POLICY" -> "PRIVACY";
            case "ABOUT", "MANIFESTO" -> "ABOUT_MANIFESTO";
            case "SCOUTING", "SCOUTING_FAQ" -> "SCOUTING_FAQ";
            default -> upper;
        };

        log.info("Executando upsert de conteúdo institucional para sectionKey='{}' (normalizado='{}'), adminId={}",
                sectionKey, normalizedKey, adminId);

        SiteContent content = siteContentRepository.findBySectionKey(normalizedKey)
                .or(() -> siteContentRepository.findBySectionKey(upper))
                .or(() -> findSectionWithAlias(normalizedKey))
                .orElseGet(() -> {
                    log.info("Seção '{}' não encontrada em site_contents. Criando novo registro (upsert).", normalizedKey);
                    return SiteContent.builder()
                            .sectionKey(normalizedKey)
                            .build();
                });

        if (dto != null) {
            if (dto.getPayloadPt() != null) {
                content.setPayloadPt(dto.getPayloadPt());
            }
            if (dto.getPayloadEn() != null) {
                content.setPayloadEn(dto.getPayloadEn());
            }
            if (dto.getMediaUrls() != null) {
                content.setMediaUrls(dto.getMediaUrls());
            }
        }
        content.setUpdatedBy(adminId);

        SiteContent saved = siteContentRepository.save(content);
        log.info("Conteúdo institucional da seção '{}' atualizado com sucesso.", normalizedKey);

        return toAdminDto(saved);
    }

    @Override
    @Transactional
    public SiteContentAdminDto updateTranslation(String sectionKey, Map<String, Object> body, UUID adminId) {
        String upper = sectionKey != null ? sectionKey.trim().toUpperCase(Locale.ROOT) : "";
        String normalizedKey = switch (upper) {
            case "TERMS", "TERMS_OF_USE" -> "TERMS";
            case "PRIVACY", "PRIVACY_POLICY" -> "PRIVACY";
            case "ABOUT", "MANIFESTO" -> "ABOUT_MANIFESTO";
            case "SCOUTING", "SCOUTING_FAQ" -> "SCOUTING_FAQ";
            default -> upper;
        };

        log.info("Executando upsert automático de tradução para sectionKey='{}' (normalizado='{}'), adminId={}",
                sectionKey, normalizedKey, adminId);

        // Se siteContentRepository.findBySectionKey(sectionKey) retornar vazio, NÃO lance 404.
        // Instancie uma nova entidade SiteContent, defina o sectionKey e salve (upsert).
        SiteContent content = siteContentRepository.findBySectionKey(normalizedKey)
                .or(() -> siteContentRepository.findBySectionKey(upper))
                .or(() -> findSectionWithAlias(normalizedKey))
                .orElseGet(() -> {
                    log.info("Seção '{}' não encontrada em site_contents. Criando novo registro (upsert).", normalizedKey);
                    return SiteContent.builder()
                            .sectionKey(normalizedKey)
                            .build();
                });

        Map<String, Object> payloadPt = new LinkedHashMap<>();
        Map<String, Object> payloadEn = new LinkedHashMap<>();

        if (body != null) {
            if (body.containsKey("translations") && body.get("translations") instanceof Map<?, ?> t) {
                if (t.get("pt") instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) payloadPt.put(e.getKey().toString(), e.getValue());
                    }
                }
                if (t.get("en") instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) payloadEn.put(e.getKey().toString(), e.getValue());
                    }
                }
            } else {
                if (body.get("pt") instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) payloadPt.put(e.getKey().toString(), e.getValue());
                    }
                }
                if (body.get("en") instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) payloadEn.put(e.getKey().toString(), e.getValue());
                    }
                }
            }

            if (body.containsKey("content") && !payloadPt.containsKey("content")) {
                payloadPt.put("content", body.get("content"));
                payloadPt.put("body", body.get("content"));
            }
            if (body.containsKey("contentEn") && !payloadEn.containsKey("content")) {
                payloadEn.put("content", body.get("contentEn"));
                payloadEn.put("body", body.get("contentEn"));
            }
            if (body.containsKey("items")) {
                payloadPt.put("items", body.get("items"));
                payloadEn.put("items", body.get("items"));
            }
        }

        content.setPayloadPt(payloadPt);
        content.setPayloadEn(payloadEn);
        content.setUpdatedBy(adminId);

        SiteContent saved = siteContentRepository.save(content);
        log.info("Tradução institucional da seção '{}' persistida com sucesso via upsert.", normalizedKey);

        return toAdminDto(saved);
    }

    @Override
    public AssetUploadResponseDto uploadAsset(MultipartFile file, String folder) {
        log.info("Iniciando upload de ativo institucional. Nome='{}', Pasta='{}'",
                file != null ? file.getOriginalFilename() : "null", folder);

        String bucket = resolveBucketName();
        validateAssetFile(file, bucket);

        String targetFolder = StringUtils.hasText(folder) ? folder.trim() : "assets";
        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
        String storagePath = String.format("%s/%s-%s", targetFolder, UUID.randomUUID(), sanitizedFilename);

        storageService.uploadFile(bucket, storagePath, file);
        String publicUrl = storageService.getPublicUrl(bucket, storagePath);

        log.info("Upload de ativo institucional concluído com sucesso: URL={}", publicUrl);

        return AssetUploadResponseDto.builder()
                .fileUrl(publicUrl)
                .storagePath(storagePath)
                .fileType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .build();
    }

    private String resolveLanguage(String lang) {
        if (StringUtils.hasText(lang) && "en".equalsIgnoreCase(lang.trim())) {
            return "en";
        }
        return "pt";
    }

    private Map<String, Object> resolvePayloadByLanguage(SiteContent content, String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            if (content.getPayloadEn() != null && !content.getPayloadEn().isEmpty()) {
                return content.getPayloadEn();
            }
            // Fallback para PT
            return content.getPayloadPt();
        }

        // Padrão PT
        if (content.getPayloadPt() != null && !content.getPayloadPt().isEmpty()) {
            return content.getPayloadPt();
        }
        return content.getPayloadEn();
    }

    private SiteContentAdminDto toAdminDto(SiteContent content) {
        return SiteContentAdminDto.builder()
                .id(content.getId())
                .sectionKey(content.getSectionKey())
                .payloadPt(content.getPayloadPt())
                .payloadEn(content.getPayloadEn())
                .mediaUrls(content.getMediaUrls())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .updatedBy(content.getUpdatedBy())
                .build();
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "file";
        }
        return filename.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String resolveBucketName() {
        return supabaseProperties.resolveBucketSiteAssets();
    }

    public static final long MAX_ASSET_SIZE_BYTES = 25L * 1024 * 1024; // 25 MB

    private void validateAssetFile(MultipartFile file, String bucket) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("O arquivo para upload é obrigatório e não pode estar vazio.");
        }
        long maxSize = MAX_ASSET_SIZE_BYTES;
        if (file.getSize() > maxSize) {
            long maxMb = maxSize / (1024 * 1024);
            throw new FileSizeExceededException(
                    String.format("O arquivo enviado (%.2f MB) excede o limite máximo permitido de %d MB para o bucket '%s'.",
                            file.getSize() / (1024.0 * 1024.0), maxMb, bucket));
        }
        String contentType = file.getContentType();
        boolean isImage = SupabaseStorageService.ALLOWED_IMAGE_TYPES.contains(contentType);
        boolean isVideo = SupabaseStorageService.ALLOWED_VIDEO_TYPES.contains(contentType);
        if (!isImage && !isVideo) {
            throw new InvalidFileException(
                    String.format("Formato de arquivo '%s' não suportado para o bucket '%s'. Tipos permitidos: imagens (%s) e vídeos (%s).",
                            contentType, bucket,
                            String.join(", ", SupabaseStorageService.ALLOWED_IMAGE_TYPES),
                            String.join(", ", SupabaseStorageService.ALLOWED_VIDEO_TYPES)));
        }
    }


    private Optional<SiteContent> findSectionWithAlias(String sectionKey) {
        if (sectionKey == null) return Optional.empty();
        String upper = sectionKey.trim().toUpperCase(Locale.ROOT);
        String normalized = switch (upper) {
            case "TERMS", "TERMS_OF_USE" -> "TERMS";
            case "PRIVACY", "PRIVACY_POLICY" -> "PRIVACY";
            case "ABOUT", "MANIFESTO" -> "ABOUT_MANIFESTO";
            default -> upper;
        };

        Optional<SiteContent> opt = siteContentRepository.findBySectionKey(normalized);
        if (opt.isPresent()) return opt;

        opt = siteContentRepository.findBySectionKey(sectionKey.trim());
        if (opt.isPresent()) return opt;

        if ("TERMS".equals(normalized)) {
            return siteContentRepository.findBySectionKey("TERMS_OF_USE");
        }
        if ("PRIVACY".equals(normalized)) {
            return siteContentRepository.findBySectionKey("PRIVACY_POLICY");
        }
        if ("ABOUT_MANIFESTO".equals(normalized)) {
            return siteContentRepository.findBySectionKey("ABOUT");
        }
        return Optional.empty();
    }
}
