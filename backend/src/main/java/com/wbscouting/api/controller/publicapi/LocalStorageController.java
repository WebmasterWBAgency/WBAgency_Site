package com.wbscouting.api.controller.publicapi;

import com.wbscouting.api.config.SupabaseProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping({"/storage/local", "/api/v1/storage/local"})
@RequiredArgsConstructor
public class LocalStorageController {

    private final SupabaseProperties supabaseProperties;

    @GetMapping("/{bucket}/**")
    public ResponseEntity<Resource> getLocalFile(
            @PathVariable String bucket,
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();
        String prefix = "/storage/local/" + bucket + "/";
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            prefix = "/api/v1/storage/local/" + bucket + "/";
            idx = uri.indexOf(prefix);
        }

        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }

        String relativePath = uri.substring(idx + prefix.length());
        if (!StringUtils.hasText(relativePath)) {
            return ResponseEntity.notFound().build();
        }

        String baseDirStr = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                ? supabaseProperties.getStorage().getLocalDir()
                : "uploads";

        Path baseDir = Paths.get(baseDirStr).toAbsolutePath().normalize();
        Path baseFolder = baseDir.resolve(bucket).normalize();
        Path targetFile = baseFolder.resolve(relativePath).normalize();

        // Proteção contra Path / Directory Traversal (CWE-22):
        // Valida se o caminho normalizado inicia estritamente com o diretório raiz configurado (baseDir) e baseFolder
        if (!targetFile.startsWith(baseFolder) || !targetFile.startsWith(baseDir)) {
            log.warn("Tentativa de path traversal detectada para path: '{}'", relativePath);
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        try {
            if (java.nio.file.Files.exists(targetFile)) {
                Path canonicalTarget = targetFile.toRealPath();
                Path canonicalBaseDir = java.nio.file.Files.exists(baseDir) ? baseDir.toRealPath() : baseDir;
                Path canonicalBaseFolder = java.nio.file.Files.exists(baseFolder) ? baseFolder.toRealPath() : baseFolder;
                if (!canonicalTarget.startsWith(canonicalBaseFolder) || !canonicalTarget.startsWith(canonicalBaseDir)) {
                    log.warn("Tentativa de canonical path traversal detectada para path: '{}'", relativePath);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                }
            }
        } catch (IOException e) {
            log.warn("Falha ao resolver caminho canônico para path '{}': {}", relativePath, e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        if (!java.nio.file.Files.exists(targetFile) || !java.nio.file.Files.isRegularFile(targetFile)) {
            log.debug("Arquivo local não encontrado: {}", targetFile);
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(targetFile.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(targetFile.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(resource);
        } catch (IOException e) {
            log.error("Erro ao carregar recurso do arquivo local: {}", targetFile, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
