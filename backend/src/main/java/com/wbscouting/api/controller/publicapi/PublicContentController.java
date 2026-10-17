package com.wbscouting.api.controller.publicapi;

import com.wbscouting.api.dto.content.SiteContentPublicDto;
import com.wbscouting.api.service.content.SiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping({"/api/v1/public/content", "/public/content", "/api/v1/public/institutional", "/public/institutional"})
public class PublicContentController {

    private final SiteContentService siteContentService;

    public PublicContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @GetMapping("/{sectionKey}")
    public ResponseEntity<SiteContentPublicDto> getPublicContent(
            @PathVariable String sectionKey,
            @RequestParam(defaultValue = "pt") String lang) {

        SiteContentPublicDto content = siteContentService.getPublicContent(sectionKey, lang);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(content);
    }

    @GetMapping({"/about", "/institutional/about"})
    public ResponseEntity<Map<String, Object>> getAboutContent(@RequestParam(defaultValue = "pt") String lang) {
        SiteContentPublicDto content = siteContentService.getPublicContent("ABOUT_MANIFESTO", lang);
        Map<String, Object> result = new LinkedHashMap<>();
        if (content.getPayload() != null) {
            result.putAll(content.getPayload());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(result);
    }

    @GetMapping
    public ResponseEntity<Map<String, Map<String, Object>>> getAllPublicContent(
            @RequestParam(defaultValue = "pt") String lang) {

        Map<String, Map<String, Object>> contentMap = siteContentService.getAllPublicContent(lang);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(contentMap);
    }
}
