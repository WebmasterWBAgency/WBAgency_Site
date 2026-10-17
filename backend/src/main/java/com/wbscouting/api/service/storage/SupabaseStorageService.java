package com.wbscouting.api.service.storage;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wbscouting.api.config.SupabaseProperties;
import com.wbscouting.api.exception.FileSizeExceededException;
import com.wbscouting.api.exception.InvalidFileException;
import com.wbscouting.api.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseStorageService implements StorageService {

    private final RestClient supabaseStorageRestClient;
    private final SupabaseProperties supabaseProperties;

    @PostConstruct
    public void init() {
        boolean configured = supabaseProperties.isKeyConfigured();
        boolean localFallback = supabaseProperties.getStorage() != null && supabaseProperties.getStorage().isLocalFallback();
        if (!configured) {
            if (localFallback) {
                log.warn("[SUPABASE STORAGE] ATENÇÃO: Chave do Supabase (SUPABASE_SERVICE_ROLE_KEY / SUPABASE_KEY) não configurada ou dummy ('{}'). Operações de storage utilizarão fallback local.",
                        supabaseProperties.getEffectiveKey());
            } else {
                log.error("[SUPABASE STORAGE] ERRO CRÍTICO: Chave administrativa do Supabase (SUPABASE_SERVICE_ROLE_KEY / SUPABASE_KEY) não configurada ou dummy ('{}'). Uploads remotos falharão até que a variável de ambiente seja definida.",
                        supabaseProperties.getEffectiveKey());
            }
        } else {
            String key = supabaseProperties.getEffectiveKey();
            String prefix = key.length() > 10 ? key.substring(0, 10) + "..." : "***";
            log.info("[SUPABASE STORAGE] Serviço inicializado com sucesso. Chave administrativa ativa: [prefixo={}]", prefix);
        }
    }

    public SupabaseProperties getProperties() {
        return supabaseProperties;
    }

    // Limites de tamanho em bytes conforme especificação dos buckets
    public static final long MAX_SIZE_SITE_ASSETS = 30L * 1024 * 1024;        // 30 MB (banners, logos, PDFs institucionais, composites publicitarios)
    public static final long MAX_SIZE_MODELS_MEDIA = 30L * 1024 * 1024;       // 30 MB (Fotos Book, Polas HD + COMPOSITE PDF export InDesign)
    public static final long MAX_SIZE_CANDIDATES_UPLOADS = 15L * 1024 * 1024; // 15 MB (Fotos WhatsApp/Instagram enviadas por candidatas)

    // Conjuntos de MIME Types permitidos por tipo de mídia
    public static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/webm"
    );

    public static final Set<String> ALLOWED_PDF_TYPES = Set.of(
            "application/pdf"
    );

    @Override
    public String uploadFile(String bucket, String path, MultipartFile file) {
        log.info("Iniciando upload de arquivo para bucket='{}', path='{}'", bucket, path);

        // 🟢 ORDEM CORRETA AGORA: Normalizar PATH ANTES do validateUpload.
        // (Bug antigo: validate era chamado com path BRUTO, e apos a validacao normalizava.
        //  Causava erros de path que comecavam com / ou \ na validacao de /composite/!)
        String normalizedPath = normalizePath(path);
        String contentType = file.getContentType();
        boolean localFallbackEnabled = supabaseProperties.getStorage() != null && supabaseProperties.getStorage().isLocalFallback();

        // 🟢 Validacao agora usa PATH JÁ NORMALIZADO (confiavel)
        validateUpload(bucket, normalizedPath, file);

        // 🟢 Sanitização defensiva e validação de magic bytes para imagens (TASK-003)
        MultipartFile fileToUpload = file;
        if (contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase().trim())) {
            fileToUpload = com.wbscouting.api.security.sanitizer.ImageSecuritySanitizer.sanitizeAndReencode(file);
            contentType = fileToUpload.getContentType();
        }

        // 1. Caso a chave do Supabase não esteja configurada ou seja dummy
        if (!supabaseProperties.isKeyConfigured()) {
            if (localFallbackEnabled) {
                log.warn("[SUPABASE STORAGE LOCAL FALLBACK] Chave do Supabase ('supabase.service-role-key' / 'supabase.key') não configurada ou com valor dummy ('{}'). Realizando salvamento local para bucket='{}', path='{}'.",
                        supabaseProperties.getEffectiveKey(), bucket, normalizedPath);
                saveFileLocally(bucket, normalizedPath, fileToUpload);
                return normalizedPath;
            } else {
                log.error("[SUPABASE STORAGE AUTH ERROR] Tentativa de upload para o bucket '{}' com chave do Supabase ausente ou inválida ('{}').",
                        bucket, supabaseProperties.getEffectiveKey());
                throw new StorageException(
                        "Chave do Supabase não configurada para upload no bucket '" + bucket + "'. Configure 'supabase.service-role-key' ou 'supabase.key'.",
                        HttpStatus.UNAUTHORIZED,
                        "Falha de Autenticação com Armazenamento",
                        "STORAGE_AUTH_FAILED"
                );
            }
        }

        // 2. Tentativa de upload remoto para o Supabase Storage
        try {
            byte[] fileBytes = fileToUpload.getBytes();
            String endpointPath = "/storage/v1/object/" + bucket + "/" + normalizedPath;

            String effectiveKey = supabaseProperties.getEffectiveKey();
            String rawKey = (effectiveKey != null) ? effectiveKey.trim() : "";
            if (rawKey.startsWith("Bearer ") || rawKey.startsWith("bearer ")) {
                rawKey = rawKey.substring(7).trim();
            }
            String authHeader = StringUtils.hasText(rawKey) ? "Bearer " + rawKey : "";

            supabaseStorageRestClient.post()
                    .uri(uriBuilder -> uriBuilder.path(endpointPath).build())
                    .header("apikey", rawKey)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header("x-upsert", "true")
                    .body(fileBytes)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes());
                        HttpStatusCode status = response.getStatusCode();
                        handleRemoteStorageError(bucket, normalizedPath, status, errorBody);
                    })
                    .toBodilessEntity();

            log.info("Upload concluído com sucesso no Supabase Storage: bucket='{}', path='{}'", bucket, normalizedPath);
            return normalizedPath;

        } catch (IOException e) {
            log.error("Falha de I/O ao ler bytes do arquivo multipart", e);
            throw new InvalidFileException("Não foi possível processar o arquivo enviado para upload.", e);
        } catch (StorageException e) {
            if (localFallbackEnabled) {
                log.warn("[SUPABASE STORAGE LOCAL FALLBACK] Falha na integração remota com Supabase Storage. Ativando fallback local para bucket='{}', path='{}'. Motivo: {}",
                        bucket, normalizedPath, e.getMessage());
                saveFileLocally(bucket, normalizedPath, file);
                return normalizedPath;
            }
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao realizar upload para o Supabase Storage", e);
            boolean isTimeoutOrNetwork = e instanceof org.springframework.web.client.ResourceAccessException
                    || e.getCause() instanceof java.net.SocketTimeoutException
                    || e.getCause() instanceof java.net.ConnectException;

            if (isTimeoutOrNetwork) {
                log.error("[SUPABASE STORAGE TIMEOUT / NETWORK ERROR] Timeout ou falha de conexão com o Supabase Storage (URL: {}). Erro: {}",
                        supabaseProperties.getUrl(), e.getMessage());
            }

            if (localFallbackEnabled) {
                log.warn("[SUPABASE STORAGE LOCAL FALLBACK] Salvando arquivo localmente devido a falha de conexão/timeout com o Supabase Storage: bucket='{}', path='{}'.",
                        bucket, normalizedPath);
                saveFileLocally(bucket, normalizedPath, file);
                return normalizedPath;
            }

            HttpStatus status = isTimeoutOrNetwork ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
            String code = isTimeoutOrNetwork ? "STORAGE_TIMEOUT" : "STORAGE_COMMUNICATION_ERROR";
            String title = isTimeoutOrNetwork ? "Timeout na Comunicação com Armazenamento" : "Falha na Comunicação com Armazenamento";
            throw new StorageException("Falha na comunicação com o serviço de armazenamento: " + e.getMessage(), e, status, title, code);
        }
    }

    @Override
    public void deleteFile(String bucket, String path) {
        if (!StringUtils.hasText(bucket)) {
            throw new InvalidFileException("O nome do bucket é obrigatório.");
        }
        if (!StringUtils.hasText(path)) {
            throw new InvalidFileException("O caminho do arquivo a ser removido é obrigatório.");
        }

        String normalizedPath = normalizePath(path);
        log.info("Removendo arquivo do bucket='{}', path='{}'", bucket, normalizedPath);

        deleteLocalFileIfExists(bucket, normalizedPath);

        if (!supabaseProperties.isKeyConfigured()) {
            log.info("[SUPABASE STORAGE LOCAL] Chave do Supabase dummy ou ausente. Exclusão remota ignorada para bucket='{}', path='{}'.", bucket, normalizedPath);
            return;
        }

        try {
            Map<String, Object> payload = Map.of("prefixes", List.of(normalizedPath));

            String effectiveKey = supabaseProperties.getEffectiveKey();
            String rawKey = (effectiveKey != null) ? effectiveKey.trim() : "";
            if (rawKey.startsWith("Bearer ") || rawKey.startsWith("bearer ")) {
                rawKey = rawKey.substring(7).trim();
            }
            String authHeader = StringUtils.hasText(rawKey) ? "Bearer " + rawKey : "";

            supabaseStorageRestClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/storage/v1/object/{bucket}", bucket)
                    .header("apikey", rawKey)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes());
                        log.error("Erro retornado pelo Supabase Storage ao deletar arquivo. Status: {}, Body: {}",
                                response.getStatusCode(), errorBody);
                        throw new StorageException("Erro no Supabase Storage durante a remoção: " + errorBody);
                    })
                    .toBodilessEntity();

            log.info("Arquivo removido com sucesso: bucket='{}', path='{}'", bucket, normalizedPath);

        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao deletar arquivo no Supabase Storage", e);
            throw new StorageException("Falha ao remover arquivo do armazenamento: " + e.getMessage(), e);
        }
    }

    @Override
    public String copyFile(String sourceBucket, String sourcePath, String destBucket, String destPath) {
        if (!StringUtils.hasText(sourceBucket) || !StringUtils.hasText(sourcePath)) {
            throw new InvalidFileException("Bucket e caminho de origem são obrigatórios para cópia.");
        }
        if (!StringUtils.hasText(destBucket) || !StringUtils.hasText(destPath)) {
            throw new InvalidFileException("Bucket e caminho de destino são obrigatórios para cópia.");
        }

        String normSource = normalizePath(sourcePath);
        String normDest = normalizePath(destPath);
        log.info("Copiando arquivo de bucket='{}', path='{}' para bucket='{}', path='{}'",
                sourceBucket, normSource, destBucket, normDest);

        // Se existir localmente, copia no sistema de arquivos local
        String baseDir = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                ? supabaseProperties.getStorage().getLocalDir()
                : "uploads";
        java.nio.file.Path localSource = java.nio.file.Paths.get(baseDir, sourceBucket, normSource).normalize();
        if (java.nio.file.Files.exists(localSource)) {
            java.nio.file.Path localDest = java.nio.file.Paths.get(baseDir, destBucket, normDest).normalize();
            try {
                if (localDest.getParent() != null) {
                    java.nio.file.Files.createDirectories(localDest.getParent());
                }
                java.nio.file.Files.copy(localSource, localDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("Arquivo copiado localmente com sucesso: {}", localDest);
                return normDest;
            } catch (IOException ioEx) {
                log.error("Erro ao copiar arquivo localmente", ioEx);
            }
        }

        if (!supabaseProperties.isKeyConfigured()) {
            return normDest;
        }

        String effectiveKey = supabaseProperties.getEffectiveKey();
        String rawKey = (effectiveKey != null) ? effectiveKey.trim() : "";
        if (rawKey.startsWith("Bearer ") || rawKey.startsWith("bearer ")) {
            rawKey = rawKey.substring(7).trim();
        }
        String authHeader = StringUtils.hasText(rawKey) ? "Bearer " + rawKey : "";

        try {
            Map<String, Object> payload = Map.of(
                    "bucketId", sourceBucket,
                    "sourceKey", normSource,
                    "destinationBucket", destBucket,
                    "destinationKey", normDest
            );

            supabaseStorageRestClient.post()
                    .uri("/storage/v1/object/copy")
                    .header("apikey", rawKey)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Arquivo copiado com sucesso no Supabase Storage: {} -> {}", normSource, normDest);
            return normDest;
        } catch (Exception ex) {
            log.warn("Tentativa de cópia direta via endpoint copy falhou ({}). Tentando download e upload...", ex.getMessage());
            try {
                byte[] bytes = supabaseStorageRestClient.get()
                        .uri("/storage/v1/object/{bucket}/{path}", sourceBucket, normSource)
                        .header("apikey", rawKey)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .body(byte[].class);

                if (bytes != null && bytes.length > 0) {
                    supabaseStorageRestClient.post()
                            .uri("/storage/v1/object/{bucket}/{path}", destBucket, normDest)
                            .header("apikey", rawKey)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .header("x-upsert", "true")
                            .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                            .body(bytes)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Arquivo migrado com sucesso via streaming: {} -> {}", normSource, normDest);
                    return normDest;
                }
            } catch (Exception e2) {
                log.error("Falha ao copiar arquivo entre buckets: {}", e2.getMessage());
                throw new StorageException("Não foi possível copiar o arquivo no storage: " + e2.getMessage(), e2);
            }
        }
        return normDest;
    }

    @Override
    public String getPublicUrl(String bucket, String path) {
        if (!StringUtils.hasText(bucket)) {
            throw new InvalidFileException("O nome do bucket é obrigatório.");
        }
        if (!StringUtils.hasText(path)) {
            log.warn("[SUPABASE STORAGE] getPublicUrl chamado com path NULO/VAZIO para bucket='{}'. URL incompleta será evitada.", bucket);
            return null;
        }

        String normalizedPath = normalizePath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            log.warn("[SUPABASE STORAGE] getPublicUrl path vazio apos normalizacao. bucket='{}', entrada='{}'", bucket, path);
            return null;
        }

        // 🔒 TASK-004: Nenhuma URL estática do bucket privado candidates-uploads pode ser exposta
        if ("candidates-uploads".equalsIgnoreCase(bucket)) {
            log.warn("[STORAGE SECURITY] Bloqueada tentativa de URL pública estática para bucket privado 'candidates-uploads'. Redirecionando para Signed URL HMAC com TTL de 900s.");
            return createSignedUrl(bucket, normalizedPath, 900);
        }

        // Se o arquivo foi salvo localmente no fallback, retorna a URL do endpoint local
        String baseDir = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                ? supabaseProperties.getStorage().getLocalDir()
                : "uploads";
        java.nio.file.Path localFile = java.nio.file.Paths.get(baseDir, bucket, normalizedPath).normalize();

        if (java.nio.file.Files.exists(localFile)) {
            String localBaseUrl = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalBaseUrl()))
                    ? sanitizeBaseUrl(supabaseProperties.getStorage().getLocalBaseUrl())
                    : "http://localhost:8080";
            return String.format("%s/api/v1/storage/local/%s/%s", localBaseUrl, bucket, normalizedPath);
        }

        String baseUrl = sanitizeBaseUrl(supabaseProperties.getUrl());
        // Garantia extra: path NÃO PODE terminar com '/' (URL incompleta!)
        while (normalizedPath.endsWith("/")) normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        if (!StringUtils.hasText(normalizedPath)) return null;

        String storageBase = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getPublicBaseUrl()))
                ? sanitizeBaseUrl(supabaseProperties.getStorage().getPublicBaseUrl())
                : String.format("%s/storage/v1/object/public", baseUrl);

        return String.format("%s/%s/%s", storageBase, bucket, normalizedPath);
    }

    /**
     * Helper de correção de URL para casos onde o banco gravou file_url INCOMPLETO ou null
     * (aconteceu por mapeamento JPA ambíguo storagePath/filePath nas primeiras versões).
     * Prioridade: (1) fileUrl valido contem nome de arquivo (NAO e so o diretorio do bucket)
     *            (2) filePath válido (monta a URL pública em tempo real)
     * Retorna null se ambos forem inválidos.
     */
    public String resolvePublicUrlFromFields(String bucket, String filePath, String fileUrlFromDb) {
        if (StringUtils.hasText(fileUrlFromDb)) {
            String trimmedUrl = fileUrlFromDb.trim();
            // Se pertencer ao bucket informado, verifica se tem path valido após o nome do bucket
            if (trimmedUrl.contains(bucket + "/")) {
                if (trimmedUrl.lastIndexOf(bucket + "/") + bucket.length() + 1 < trimmedUrl.length()) {
                    return trimmedUrl;
                }
            } else if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
                // Se for URL externa completa (ex: Unsplash, CDN externo ou link externo do banco)
                // Se filePath for vazio, dummy (ex: 'models/a1/cover.jpg') ou a URL for externa, preserva a URL web válida
                if (!StringUtils.hasText(filePath) || filePath.startsWith("models/") || !trimmedUrl.contains("supabase.co")) {
                    return trimmedUrl;
                }
            }
        }

        if (StringUtils.hasText(filePath)) {
            return getPublicUrl(bucket, filePath);
        }
        return fileUrlFromDb;
    }

    @Override
    public String generateSignedUrl(String bucket, String storagePath, int expiresInSeconds) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }

        // Normaliza o path caso venha como URL completa ou com query parameters
        String relativePath = storagePath.trim();
        if (bucket != null && !bucket.isBlank() && relativePath.contains(bucket + "/")) {
            relativePath = relativePath.substring(relativePath.indexOf(bucket + "/") + bucket.length() + 1);
        }
        if (relativePath.contains("?")) {
            relativePath = relativePath.substring(0, relativePath.indexOf("?"));
        }

        String normalizedPath = normalizePath(relativePath);
        if (normalizedPath.isBlank()) {
            return null;
        }

        int defaultTtl = "candidates-uploads".equalsIgnoreCase(bucket) ? 900 : 3600;
        int ttl = expiresInSeconds > 0 ? expiresInSeconds : defaultTtl;

        // Se existir localmente, retorna URL local direta
        String baseDir = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                ? supabaseProperties.getStorage().getLocalDir()
                : "uploads";
        java.nio.file.Path localFile = java.nio.file.Paths.get(baseDir, bucket, normalizedPath).normalize();
        if (java.nio.file.Files.exists(localFile)) {
            String localBaseUrl = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalBaseUrl()))
                    ? sanitizeBaseUrl(supabaseProperties.getStorage().getLocalBaseUrl())
                    : "http://localhost:8080";
            return String.format("%s/api/v1/storage/local/%s/%s?expiresIn=%d", localBaseUrl, bucket, normalizedPath, ttl);
        }

        boolean localFallbackEnabled = supabaseProperties.getStorage() != null && supabaseProperties.getStorage().isLocalFallback();
        if (!supabaseProperties.isKeyConfigured() && localFallbackEnabled) {
            String localBaseUrl = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalBaseUrl()))
                    ? sanitizeBaseUrl(supabaseProperties.getStorage().getLocalBaseUrl())
                    : "http://localhost:8080";
            return String.format("%s/api/v1/storage/local/%s/%s?expiresIn=%d", localBaseUrl, bucket, normalizedPath, ttl);
        }

        log.debug("Gerando Signed URL para bucket='{}', path='{}', expiraEm={}s", bucket, normalizedPath, ttl);

        try {
            Map<String, Object> payload = Map.of("expiresIn", ttl);
            String endpointPath = "/storage/v1/object/sign/" + bucket + "/" + normalizedPath;

            String effectiveKey = supabaseProperties.getEffectiveKey();
            String rawKey = (effectiveKey != null) ? effectiveKey.trim() : "";
            if (rawKey.startsWith("Bearer ") || rawKey.startsWith("bearer ")) {
                rawKey = rawKey.substring(7).trim();
            }
            String authHeader = StringUtils.hasText(rawKey) ? "Bearer " + rawKey : "";

            SignedUrlResponse response = supabaseStorageRestClient.post()
                    .uri(uriBuilder -> uriBuilder.path(endpointPath).build())
                    .header("apikey", rawKey)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .body(SignedUrlResponse.class);

            if (response != null && StringUtils.hasText(response.getSignedUrl())) {
                String signedFragment = response.getSignedUrl().trim();
                if (signedFragment.startsWith("http://") || signedFragment.startsWith("https://")) {
                    return signedFragment;
                }

                String baseUrl = sanitizeBaseUrl(supabaseProperties.getUrl());
                if (signedFragment.startsWith("/storage/v1")) {
                    return baseUrl + signedFragment;
                }
                if (signedFragment.startsWith("/")) {
                    return baseUrl + "/storage/v1" + signedFragment;
                }
                return baseUrl + "/storage/v1/" + signedFragment;
            }
        } catch (org.springframework.web.client.RestClientResponseException rcre) {
            if (rcre.getStatusCode().value() == 401 || rcre.getStatusCode().value() == 403) {
                throw new StorageException("Erro de autenticação no Supabase Storage: " + rcre.getResponseBodyAsString(),
                        HttpStatus.UNAUTHORIZED, "Falha de Autenticação com Armazenamento", "STORAGE_AUTH_FAILED");
            }
            if (rcre.getStatusCode().value() == 404) {
                throw new StorageException("Recurso não encontrado no Supabase Storage: " + rcre.getResponseBodyAsString(),
                        HttpStatus.NOT_FOUND, "Recurso Não Encontrado", "STORAGE_NOT_FOUND");
            }
            log.error("Erro HTTP ao gerar Signed URL para path '{}': {}", relativePath, rcre.getMessage());
            if (localFallbackEnabled) {
                String localBaseUrl = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalBaseUrl()))
                        ? sanitizeBaseUrl(supabaseProperties.getStorage().getLocalBaseUrl())
                        : "http://localhost:8080";
                return String.format("%s/api/v1/storage/local/%s/%s?expiresIn=%d", localBaseUrl, bucket, normalizedPath, ttl);
            }
            return null;
        } catch (Exception ex) {
            log.error("Erro ao gerar Signed URL para path '{}': {}", relativePath, ex.getMessage());
            if (localFallbackEnabled) {
                String localBaseUrl = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalBaseUrl()))
                        ? sanitizeBaseUrl(supabaseProperties.getStorage().getLocalBaseUrl())
                        : "http://localhost:8080";
                return String.format("%s/api/v1/storage/local/%s/%s?expiresIn=%d", localBaseUrl, bucket, normalizedPath, ttl);
            }
        }
        return null;
    }

    @Override
    public String createSignedUrl(String bucket, String path, int expiresInSeconds) {
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("Tempo de expiração para Signed URL deve ser maior que zero.");
        }
        int defaultTtl = "candidates-uploads".equalsIgnoreCase(bucket) ? 900 : 3600;
        int ttl = expiresInSeconds > 0 ? expiresInSeconds : defaultTtl;
        return generateSignedUrl(bucket, path, ttl);
    }

    private void handleRemoteStorageError(String bucket, String path, HttpStatusCode status, String errorBody) {
        if (status.value() == 401 || status.value() == 403) {
            log.error("[SUPABASE STORAGE AUTH ERROR] Falha de autenticação no Supabase Storage ao acessar o bucket '{}'. Status: {}, Body: {}. Verifique se 'supabase.service-role-key' ou 'supabase.key' é válida.",
                    bucket, status, errorBody);
            throw new StorageException(
                    "Erro de autenticação no Supabase Storage durante o upload: " + errorBody,
                    HttpStatus.UNAUTHORIZED,
                    "Falha de Autenticação com Armazenamento",
                    "STORAGE_AUTH_FAILED"
            );
        } else if (status.value() == 404 || (errorBody != null && errorBody.toLowerCase().contains("bucket not found"))) {
            log.error("[SUPABASE STORAGE BUCKET NOT FOUND] Bucket '{}' não foi encontrado no Supabase Storage. Status: 404, Body: {}. Certifique-se de que o bucket foi criado no painel do Supabase.",
                    bucket, errorBody);
            throw new StorageException(
                    "Bucket '" + bucket + "' não foi encontrado no Supabase Storage: " + errorBody,
                    HttpStatus.NOT_FOUND,
                    "Bucket de Armazenamento Não Encontrado",
                    "STORAGE_BUCKET_NOT_FOUND"
            );
        } else {
            log.error("Erro retornado pelo Supabase Storage no upload. Status: {}, Body: {}", status, errorBody);
            throw new StorageException("Erro no Supabase Storage durante o upload: " + errorBody);
        }
    }

    private void saveFileLocally(String bucket, String normalizedPath, MultipartFile file) {
        try {
            String baseDir = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                    ? supabaseProperties.getStorage().getLocalDir()
                    : "uploads";
            java.nio.file.Path targetPath = java.nio.file.Paths.get(baseDir, bucket, normalizedPath).normalize();
            if (targetPath.getParent() != null) {
                java.nio.file.Files.createDirectories(targetPath.getParent());
            }
            java.nio.file.Files.write(targetPath, file.getBytes(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[SUPABASE STORAGE LOCAL] Arquivo salvo localmente em: {}", targetPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Falha ao salvar arquivo no armazenamento local de fallback: {}", e.getMessage(), e);
            throw new StorageException("Falha ao salvar arquivo no fallback local: " + e.getMessage(), e,
                    HttpStatus.INTERNAL_SERVER_ERROR, "Falha no Armazenamento Local", "STORAGE_LOCAL_FALLBACK_FAILED");
        }
    }

    private void deleteLocalFileIfExists(String bucket, String normalizedPath) {
        try {
            String baseDir = (supabaseProperties.getStorage() != null && StringUtils.hasText(supabaseProperties.getStorage().getLocalDir()))
                    ? supabaseProperties.getStorage().getLocalDir()
                    : "uploads";
            java.nio.file.Path targetPath = java.nio.file.Paths.get(baseDir, bucket, normalizedPath).normalize();
            if (java.nio.file.Files.exists(targetPath)) {
                java.nio.file.Files.delete(targetPath);
                log.info("[SUPABASE STORAGE LOCAL] Arquivo local excluído: {}", targetPath.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Falha não-crítica ao tentar excluir arquivo local: {}", e.getMessage());
        }
    }

    /**
     * Validações fail-fast de integridade, tamanho e formato MIME do arquivo
     */
    private void validateUpload(String bucket, String path, MultipartFile file) {
        if (!StringUtils.hasText(bucket)) {
            throw new InvalidFileException("O nome do bucket deve ser informado.");
        }
        if (!StringUtils.hasText(path)) {
            throw new InvalidFileException("O caminho de destino do arquivo deve ser informado.");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("O arquivo enviado não pode ser nulo ou vazio.");
        }

        String rawContentType = file.getContentType();
        if (!StringUtils.hasText(rawContentType)) {
            throw new InvalidFileException("O Content-Type do arquivo não foi identificado.");
        }
        String contentType = rawContentType.toLowerCase().trim();

        long fileSize = file.getSize();

        // Mapeamento dos limites e validação estrita por bucket
        if ("site-assets".equalsIgnoreCase(bucket)) {
            if (fileSize > MAX_SIZE_SITE_ASSETS) {
                final double maxMb = MAX_SIZE_SITE_ASSETS / (1024.0 * 1024.0);
                throw new FileSizeExceededException(String.format(
                        "O arquivo excede o limite máximo permitido de %.0f MB para o bucket 'site-assets'. Tamanho enviado: %.2f MB",
                        maxMb, fileSize / (1024.0 * 1024.0)));
            }
            boolean isAllowed = ALLOWED_IMAGE_TYPES.contains(contentType)
                    || ALLOWED_VIDEO_TYPES.contains(contentType)
                    || ALLOWED_PDF_TYPES.contains(contentType);
            if (!isAllowed) {
                throw new InvalidFileException(String.format(
                        "Tipo de arquivo '%s' não permitido para o bucket 'site-assets'. Permitidos: Imagens (JPEG, PNG, WEBP), Vídeos (MP4, WEBM) e PDF.",
                        contentType));
            }
        } else if ("models-media".equalsIgnoreCase(bucket)) {
            if (fileSize > MAX_SIZE_MODELS_MEDIA) {
                final double maxMb = MAX_SIZE_MODELS_MEDIA / (1024.0 * 1024.0);
                throw new FileSizeExceededException(String.format(
                        "O arquivo excede o limite máximo permitido de %.0f MB para o bucket 'models-media'. Tamanho enviado: %.2f MB",
                        maxMb, fileSize / (1024.0 * 1024.0)));
            }
            // 🟢 HOTFINAL: models-media ACEITA IMAGENS (Book/Pola) + PDF (Composite) SEMPRE.
            // A regra de negocio (PDF SOMENTE para composite, nao para book/pola) ja eh
            // ENFORCADA 100% na camada Service ModelMediaServiceImpl:
            //   - uploadModelMedia(BOOK/POLAROID) = nao aceita PDF jamais
            //   - uploadOrReplaceComposite(COMPOSITE) = aceita PDF + imagens
            // Nao precisamos de validacao DUPLICADA aqui que depende de string /composite/ no path.
            boolean isAllowedType = ALLOWED_IMAGE_TYPES.contains(contentType)
                    || ALLOWED_PDF_TYPES.contains(contentType);
            if (!isAllowedType) {
                throw new InvalidFileException(String.format(
                        "Tipo de arquivo '%s' não permitido para o bucket 'models-media'. " +
                        "Formatos permitidos: Imagens (JPEG, PNG, WEBP) para Acervo Visual Book & Polaroids " +
                        "e PDF ou Imagens (JPEG, PNG, WEBP) para Composite Oficial.",
                        contentType));
            }
        } else if ("candidates-uploads".equalsIgnoreCase(bucket)) {
            if (fileSize > MAX_SIZE_CANDIDATES_UPLOADS) {
                final double maxMb = MAX_SIZE_CANDIDATES_UPLOADS / (1024.0 * 1024.0);
                throw new FileSizeExceededException(String.format(
                        "O arquivo excede o limite máximo permitido de %.0f MB para o bucket 'candidates-uploads'. Tamanho enviado: %.2f MB",
                        maxMb, fileSize / (1024.0 * 1024.0)));
            }
            if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                throw new InvalidFileException(String.format(
                        "Tipo de arquivo '%s' não permitido para o bucket 'candidates-uploads'. " +
                        "Apenas imagens JPEG, PNG e WEBP são aceitas no formulário de cadastro de candidatas.",
                        contentType));
            }
        } else {
            throw new InvalidFileException(String.format("Bucket '%s' não é suportado para uploads.", bucket));
        }
    }

    private String normalizePath(String path) {
        String cleaned = path.trim().replace("\\", "/");
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    private String sanitizeBaseUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public static class SignedUrlResponse {
        @JsonProperty("signedURL")
        @JsonAlias({"signedUrl", "signed_url"})
        private String signedUrl;

        public String getSignedUrl() {
            return signedUrl;
        }

        public void setSignedUrl(String signedUrl) {
            this.signedUrl = signedUrl;
        }
    }
}
