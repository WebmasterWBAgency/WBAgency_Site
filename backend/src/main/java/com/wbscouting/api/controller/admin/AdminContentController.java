package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.ApplyFaqDto;
import com.wbscouting.api.dto.content.AssetUploadResponseDto;
import com.wbscouting.api.dto.content.SiteContentAdminDto;
import com.wbscouting.api.dto.content.SiteContentUpdateRequestDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.service.content.ApplyFaqService;
import com.wbscouting.api.service.content.SiteContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/content", "/admin/content", "/api/v1/admin/institutional", "/admin/institutional"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class AdminContentController {

    private final SiteContentService siteContentService;
    private final ApplyFaqService applyFaqService;

    @GetMapping
    public ResponseEntity<List<SiteContentAdminDto>> getAllAdminContent() {
        List<SiteContentAdminDto> contents = siteContentService.getAllAdminContent();
        return ResponseEntity.ok(contents);
    }

    @PutMapping("/{sectionKey}")
    public ResponseEntity<SiteContentAdminDto> updateContent(
            @PathVariable String sectionKey,
            @Valid @RequestBody SiteContentUpdateRequestDto request,
            Authentication authentication) {

        UUID adminId = null;
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Admin admin) {
            adminId = admin.getId();
        }

        SiteContentAdminDto updated = siteContentService.updateContent(sectionKey, request, adminId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping({"/translations/{sectionKey}", "/institutional/translations/{sectionKey}"})
    public ResponseEntity<Map<String, Object>> getTranslations(@PathVariable String sectionKey) {
        String upper = sectionKey != null ? sectionKey.trim().toUpperCase(Locale.ROOT) : "";
        String normalizedKey = switch (upper) {
            case "TERMS", "TERMS_OF_USE" -> "TERMS";
            case "PRIVACY", "PRIVACY_POLICY" -> "PRIVACY";
            case "ABOUT", "MANIFESTO" -> "ABOUT_MANIFESTO";
            case "SCOUTING", "SCOUTING_FAQ" -> "SCOUTING_FAQ";
            default -> upper;
        };

        if ("SCOUTING_FAQ".equals(normalizedKey) || "SCOUTING".equalsIgnoreCase(upper)) {
            List<ApplyFaqDto> faqs = applyFaqService.getAllFaqs();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sectionKey", "SCOUTING");
            response.put("title", "Configurações do Form. de Quero ser Modelo");
            Map<String, Object> translations = new LinkedHashMap<>();
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("items", faqs);
            Map<String, Object> en = new LinkedHashMap<>();
            en.put("items", faqs);
            translations.put("pt", pt);
            translations.put("en", en);
            response.put("translations", translations);
            response.put("payloadPt", pt);
            response.put("payloadEn", en);
            response.put("items", faqs);
            return ResponseEntity.ok(response);
        }

        List<SiteContentAdminDto> all = siteContentService.getAllAdminContent();
        Optional<SiteContentAdminDto> match = all.stream()
                .filter(c -> normalizedKey.equalsIgnoreCase(c.getSectionKey()) || upper.equalsIgnoreCase(c.getSectionKey()))
                .findFirst();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sectionKey", normalizedKey);
        response.put("title", normalizedKey);

        Map<String, Object> translations = new LinkedHashMap<>();
        Map<String, Object> pt = match.map(SiteContentAdminDto::getPayloadPt).orElseGet(Collections::emptyMap);
        Map<String, Object> en = match.map(SiteContentAdminDto::getPayloadEn).orElseGet(Collections::emptyMap);

        translations.put("pt", pt);
        translations.put("en", en);
        response.put("translations", translations);
        response.put("payloadPt", pt);
        response.put("payloadEn", en);

        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    @RequestMapping(
            method = {RequestMethod.PUT, RequestMethod.POST},
            value = {"/translations/{sectionKey}", "/institutional/translations/{sectionKey}"}
    )
    public ResponseEntity<SiteContentAdminDto> updateTranslations(
            @PathVariable String sectionKey,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        UUID adminId = null;
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Admin admin) {
            adminId = admin.getId();
        }

        String upper = sectionKey != null ? sectionKey.trim().toUpperCase(Locale.ROOT) : "";
        String normalizedKey = switch (upper) {
            case "TERMS", "TERMS_OF_USE" -> "TERMS";
            case "PRIVACY", "PRIVACY_POLICY" -> "PRIVACY";
            case "ABOUT", "MANIFESTO" -> "ABOUT_MANIFESTO";
            case "SCOUTING", "SCOUTING_FAQ" -> "SCOUTING_FAQ";
            default -> upper;
        };

        // 3. Se a seção for SCOUTING, garanta o redirecionamento/persistência compatível com as perguntas de FAQ em apply_faqs
        if ("SCOUTING_FAQ".equals(normalizedKey) || "SCOUTING".equalsIgnoreCase(upper)) {
            log.info("Persistindo FAQ de SCOUTING via updateTranslations para sectionKey='{}'", sectionKey);
            Object itemsObj = null;
            if (body != null) {
                if (body.containsKey("items")) {
                    itemsObj = body.get("items");
                } else if (body.get("translations") instanceof Map<?, ?> t && t.get("pt") instanceof Map<?, ?> ptMap && ptMap.containsKey("items")) {
                    itemsObj = ptMap.get("items");
                } else if (body.get("pt") instanceof Map<?, ?> ptMap && ptMap.containsKey("items")) {
                    itemsObj = ptMap.get("items");
                }
            }
            if (itemsObj != null) {
                try {
                    applyFaqService.saveBatch(itemsObj);
                    log.info("Perguntas de FAQ salvas com sucesso em apply_faqs para SCOUTING");
                } catch (Exception ex) {
                    log.warn("Erro ao salvar lote de FAQ em apply_faqs: {}", ex.getMessage());
                }
            }
        }

        SiteContentAdminDto updated = siteContentService.updateTranslation(sectionKey, body, adminId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping({"/about", "/institutional/about"})
    public ResponseEntity<Map<String, Object>> getAboutAdmin() {
        List<SiteContentAdminDto> all = siteContentService.getAllAdminContent();
        Optional<SiteContentAdminDto> match = all.stream()
                .filter(c -> "ABOUT_MANIFESTO".equalsIgnoreCase(c.getSectionKey()) || "ABOUT".equalsIgnoreCase(c.getSectionKey()))
                .findFirst();
        Map<String, Object> pt = match.map(SiteContentAdminDto::getPayloadPt).orElseGet(Collections::emptyMap);
        return ResponseEntity.ok(pt);
    }

    @PutMapping({"/about", "/institutional/about"})
    public ResponseEntity<SiteContentAdminDto> updateAboutAdmin(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        UUID adminId = null;
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Admin admin) {
            adminId = admin.getId();
        }
        SiteContentUpdateRequestDto dto = new SiteContentUpdateRequestDto();
        dto.setPayloadPt(body);
        dto.setPayloadEn(body);
        SiteContentAdminDto updated = siteContentService.updateContent("ABOUT_MANIFESTO", dto, adminId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetUploadResponseDto> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "assets") String folder) {

        AssetUploadResponseDto response = siteContentService.uploadAsset(file, folder);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
