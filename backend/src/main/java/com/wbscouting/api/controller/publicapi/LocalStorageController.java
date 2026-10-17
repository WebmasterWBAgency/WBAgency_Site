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

        // Blindagem contra Path / Directory Traversal contendo '../'
        if (bucket.contains("..") || bucket.contains("/") || bucket.contains("\\")
                || relativePath.contains("../") || relativePath.contains("..\\")
                || uri.contains("../") || uri.contains("..\\")) {
            log.warn("Tentativa de Path Traversal contendo '../' detectada. URI='{}', relativePath='{}', bucket='{}'",
                    uri, relativePath, bucket);
            throw new SecurityException("Acesso negado: tentativa de Path Traversal contendo '../' detectada");
        }

        String baseDirStr = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                ? supabaseProperties.getStorage().getLocalDir()
                : "uploads";

        Path baseDir = Paths.get(baseDirStr).toAbsolutePath().normalize();
        Path basePath = baseDir.resolve(bucket).normalize();
        Path targetPath = basePath.resolve(relativePath).normalize();

        // Validação estrita: o caminho resolvido normalizado DEVE iniciar com o diretório base configurado
        if (!targetPath.startsWith(basePath) || !targetPath.startsWith(baseDir)) {
            log.warn("Tentativa de Path Traversal detectada: targetPath '{}' não inicia com basePath '{}'", targetPath, basePath);
            throw new SecurityException("Acesso negado: o caminho resolvido não pertence ao diretório base configurado");
        }

        try {
            if (java.nio.file.Files.exists(targetPath)) {
                Path canonicalTarget = targetPath.toRealPath();
                Path canonicalBaseDir = java.nio.file.Files.exists(baseDir) ? baseDir.toRealPath() : baseDir;
                Path canonicalBasePath = java.nio.file.Files.exists(basePath) ? basePath.toRealPath() : basePath;
                if (!canonicalTarget.startsWith(canonicalBasePath) || !canonicalTarget.startsWith(canonicalBaseDir)) {
                    log.warn("Tentativa de canonical path traversal detectada para path: '{}'", relativePath);
                    throw new SecurityException("Acesso negado: o caminho canônico não pertence ao diretório base");
                }
            }
        } catch (IOException e) {
            log.warn("Falha ao resolver caminho canônico para path '{}': {}", relativePath, e.getMessage());
            throw new SecurityException("Acesso negado: erro ao validar integridade do caminho", e);
        }

        if (!java.nio.file.Files.exists(targetPath) || !java.nio.file.Files.isRegularFile(targetPath)) {
            log.debug("Arquivo local não encontrado: {}", targetPath);
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(targetPath.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(targetPath.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(resource);
        } catch (IOException e) {
            log.error("Erro ao carregar recurso do arquivo local: {}", targetPath, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
