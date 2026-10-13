package com.wbscouting.api.controller.admin;


import com.wbscouting.api.constant.ContentSectionKey;
import com.wbscouting.api.dto.content.AssetUploadResponseDto;
import com.wbscouting.api.dto.content.HomeContentDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.entity.SiteContent;
import com.wbscouting.api.repository.SiteContentRepository;
import com.wbscouting.api.security.audit.AuditAction;
import com.wbscouting.api.service.content.SiteContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminInstitutionalHomeController {

    private final SiteContentRepository siteContentRepository;
    private final SiteContentService siteContentService;

    /**
     * Endpoint PÚBLICO para leitura das configurações e disclaimer da Home
     */
    @GetMapping({"/api/v1/home-settings", "/home-settings", "/api/v1/public/home-settings", "/public/home-settings"})
    @Transactional(readOnly = true)
    public ResponseEntity<HomeContentDto> getPublicHomeSettings() {
        return ResponseEntity.ok(loadHomeSettingsDto());
    }

    /**
     * Endpoint ADMINISTRATIVO para leitura das configurações da Home
     */
    @GetMapping({"/api/v1/admin/institutional/home", "/admin/institutional/home"})
    @PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<HomeContentDto> getAdminHomeSettings() {
        return ResponseEntity.ok(loadHomeSettingsDto());
    }

    /**
     * Endpoint ADMINISTRATIVO para salvar alterações de textos, mídias e aviso/disclaimer da Home
     */
    @PutMapping({"/api/v1/admin/institutional/home", "/admin/institutional/home"})
    @PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
    @AuditAction(action = "UPDATE", resource = "INSTITUTIONAL_CONTENT", description = "Atualização de textos e disclaimer da Home")
    @Transactional
    public ResponseEntity<HomeContentDto> updateHomeSettings(
            @RequestBody HomeContentDto request,
            Authentication authentication) {

        UUID adminId = extractAdminId(authentication);

        SiteContent content = siteContentRepository.findBySectionKey(ContentSectionKey.HOME_HERO)
                .or(() -> siteContentRepository.findBySectionKey("home_hero"))
                .or(() -> siteContentRepository.findBySectionKey("home"))
                .orElseGet(() -> SiteContent.builder()
                        .sectionKey(ContentSectionKey.HOME_HERO)
                        .build());

        Map<String, Object> payloadPt = content.getPayloadPt() != null ? new LinkedHashMap<>(content.getPayloadPt()) : new LinkedHashMap<>();
        Map<String, Object> payloadEn = content.getPayloadEn() != null ? new LinkedHashMap<>(content.getPayloadEn()) : new LinkedHashMap<>();
        Map<String, Object> mediaUrls = content.getMediaUrls() != null ? new LinkedHashMap<>(content.getMediaUrls()) : new LinkedHashMap<>();

        if (request.getHeroTitle() != null) payloadPt.put("heroTitle", request.getHeroTitle());
        if (request.getHeroSubtitle() != null) payloadPt.put("heroSubtitle", request.getHeroSubtitle());
        if (request.getHeroDescription() != null) payloadPt.put("heroDescription", request.getHeroDescription());
        if (request.getScrollLabel() != null) payloadPt.put("scrollLabel", request.getScrollLabel());
        if (request.getMetaTitle() != null) payloadPt.put("metaTitle", request.getMetaTitle());
        if (request.getMetaDescription() != null) payloadPt.put("metaDescription", request.getMetaDescription());

        // Disclaimer
        if (request.getDisclaimerActive() != null) payloadPt.put("disclaimerActive", request.getDisclaimerActive());
        if (request.getDisclaimerTitle() != null) payloadPt.put("disclaimerTitle", request.getDisclaimerTitle());
        if (request.getDisclaimerText() != null) payloadPt.put("disclaimerText", request.getDisclaimerText());
        if (request.getDisclaimerLinkUrl() != null) payloadPt.put("disclaimerLinkUrl", request.getDisclaimerLinkUrl());
        if (request.getDisclaimerLinkLabel() != null) payloadPt.put("disclaimerLinkLabel", request.getDisclaimerLinkLabel());

        // Mídias
        if (request.getVideoUrl() != null) {
            mediaUrls.put("videoUrl", request.getVideoUrl());
            payloadPt.put("videoUrl", request.getVideoUrl());
        }
        if (request.getPosterUrl() != null) {
            mediaUrls.put("posterUrl", request.getPosterUrl());
            payloadPt.put("posterUrl", request.getPosterUrl());
        }

        content.setPayloadPt(payloadPt);
        content.setPayloadEn(payloadEn);
        content.setMediaUrls(mediaUrls);
        content.setUpdatedBy(adminId);

        SiteContent saved = siteContentRepository.save(content);
        log.info("Configurações da Home e Disclaimer salvos com sucesso no Supabase. ID={}", saved.getId());

        return ResponseEntity.ok(toDto(saved));
    }

    /**
     * Upload de vídeo para a Home
     */
    @PutMapping({"/api/v1/admin/institutional/home/video", "/admin/institutional/home/video"})
    @PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, String>> uploadHomeVideo(@RequestParam("file") MultipartFile file) {
        AssetUploadResponseDto uploaded = siteContentService.uploadAsset(file, "home");
        return ResponseEntity.ok(Map.of("videoUrl", uploaded.getFileUrl()));
    }

    /**
     * Upload de poster/capa para a Home
     */
    @PutMapping({"/api/v1/admin/institutional/home/poster", "/admin/institutional/home/poster"})
    @PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, String>> uploadHomePoster(@RequestParam("file") MultipartFile file) {
        AssetUploadResponseDto uploaded = siteContentService.uploadAsset(file, "home");
        return ResponseEntity.ok(Map.of("posterUrl", uploaded.getFileUrl()));
    }

    private HomeContentDto loadHomeSettingsDto() {
        Optional<SiteContent> contentOpt = siteContentRepository.findBySectionKey(ContentSectionKey.HOME_HERO)
                .or(() -> siteContentRepository.findBySectionKey("home_hero"))
                .or(() -> siteContentRepository.findBySectionKey("home"));

        if (contentOpt.isPresent()) {
            return toDto(contentOpt.get());
        }

        // Se não existir, retorna os dados padrão elegantes da WB Agency
        return HomeContentDto.builder()
                .heroTitle("WB AGENCY")
                .heroSubtitle("EDITORIAL & HIGH FASHION SCOUTING")
                .scrollLabel("SCROLL")
                .videoUrl("/assets/videos/wb-presentation.mp4")
                .posterUrl("/assets/images/hero-poster.jpg")
                .disclaimerActive(false)
                .disclaimerTitle("COMUNICADO OFICIAL • AVISO IMPORTANTE")
                .disclaimerText("Atenção: A WB Agency nunca cobra taxas para avaliação de casting ou inscrição de novos talentos.")
                .disclaimerLinkUrl("/apply")
                .disclaimerLinkLabel("SAIBA MAIS")
                .build();
    }

    private HomeContentDto toDto(SiteContent content) {
        Map<String, Object> pt = content.getPayloadPt() != null ? content.getPayloadPt() : Collections.emptyMap();
        Map<String, Object> media = content.getMediaUrls() != null ? content.getMediaUrls() : Collections.emptyMap();

        String videoUrl = (String) media.getOrDefault("videoUrl", pt.get("videoUrl"));
        String posterUrl = (String) media.getOrDefault("posterUrl", pt.get("posterUrl"));

        Boolean disclaimerActive = null;
        if (pt.get("disclaimerActive") instanceof Boolean b) {
            disclaimerActive = b;
        } else if (pt.get("disclaimerActive") != null) {
            disclaimerActive = Boolean.parseBoolean(pt.get("disclaimerActive").toString());
        }

        return HomeContentDto.builder()
                .heroTitle((String) pt.getOrDefault("heroTitle", "WB AGENCY"))
                .heroSubtitle((String) pt.getOrDefault("heroSubtitle", "EDITORIAL & HIGH FASHION SCOUTING"))
                .heroDescription((String) pt.get("heroDescription"))
                .scrollLabel((String) pt.getOrDefault("scrollLabel", "SCROLL"))
                .metaTitle((String) pt.get("metaTitle"))
                .metaDescription((String) pt.get("metaDescription"))
                .videoUrl(videoUrl != null ? videoUrl : "/assets/videos/wb-presentation.mp4")
                .posterUrl(posterUrl != null ? posterUrl : "/assets/images/hero-poster.jpg")
                .disclaimerActive(disclaimerActive != null ? disclaimerActive : false)
                .disclaimerTitle((String) pt.getOrDefault("disclaimerTitle", ""))
                .disclaimerText((String) pt.getOrDefault("disclaimerText", ""))
                .disclaimerLinkUrl((String) pt.getOrDefault("disclaimerLinkUrl", ""))
                .disclaimerLinkLabel((String) pt.getOrDefault("disclaimerLinkLabel", ""))
                .build();
    }

    private UUID extractAdminId(Authentication authentication) {
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Admin admin) {
            return admin.getId();
        }
        return null;
    }
}
