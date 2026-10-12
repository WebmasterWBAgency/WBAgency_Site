package com.wbscouting.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço de gerenciamento de lista negra de tokens JWT revogados (Hard Logout defensivo).
 * Armazena hashes SHA-256 dos tokens com seus respectivos prazos de expiração para
 * evitar vazamento de memória e garantir invalidação imediata em memória.
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();
    private final Map<String, Instant> userRevocations = new ConcurrentHashMap<>();

    /**
     * Invalida todas as sessões ativas do usuário identificado por e-mail ou username.
     */
    public void revokeAllUserTokens(String usernameOrEmail) {
        if (usernameOrEmail != null && !usernameOrEmail.isBlank()) {
            userRevocations.put(usernameOrEmail.trim().toLowerCase(java.util.Locale.ROOT), Instant.now());
            log.info("Todas as sessões ativas do usuário {} foram revogadas.", usernameOrEmail);
        }
    }

    /**
     * Invalida todas as sessões ativas do usuário identificado por ID único (UUID).
     */
    public void revokeAllUserTokens(UUID userId) {
        if (userId != null) {
            revokeAllUserTokens(userId.toString());
        }
    }

    /**
     * Verifica se as sessões do usuário foram revogadas antes ou na data informada.
     */
    public boolean isUserRevoked(String usernameOrEmail, Date issuedAt) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return false;
        }
        Instant revokedAt = userRevocations.get(usernameOrEmail.trim().toLowerCase(java.util.Locale.ROOT));
        if (revokedAt == null) {
            return false;
        }
        if (issuedAt == null) {
            return true;
        }
        return issuedAt.toInstant().isBefore(revokedAt);
    }

    /**
     * Adiciona o token informado à lista negra até o término de sua validade natural.
     *
     * @param token          o token JWT a ser revogado
     * @param expirationDate a data de expiração original do token
     */
    public void blacklistToken(String token, Date expirationDate) {
        if (token == null || token.isBlank()) {
            return;
        }

        String tokenKey = hashToken(token);
        Instant expiresAt = expirationDate != null
                ? expirationDate.toInstant()
                : Instant.now().plusSeconds(8 * 3600); // 8 horas padrão caso não informada

        blacklist.put(tokenKey, expiresAt);
        log.info("Token adicionado à blacklist de revogação. Expiração em: {}", expiresAt);
    }

    /**
     * Verifica se o token informado foi revogado ou consta na lista negra.
     *
     * @param token o token JWT a verificar
     * @return true se o token estiver revogado, false caso contrário
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String tokenKey = hashToken(token);
        Instant expiresAt = blacklist.get(tokenKey);

        if (expiresAt == null) {
            return false;
        }

        // Se o token já passou de sua vida útil original, remove da lista negra
        if (Instant.now().isAfter(expiresAt)) {
            blacklist.remove(tokenKey);
            return false;
        }

        return true;
    }

    /**
     * Limpa periodicamente entradas expiradas da lista negra.
     */
    @Scheduled(fixedRate = 3600000) // A cada 1 hora
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        blacklist.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        userRevocations.entrySet().removeIf(entry -> now.isAfter(entry.getValue().plusSeconds(8 * 3600)));
        log.debug("Limpeza periódica de tokens expirados da blacklist concluída.");
    }

    /**
     * Gera hash SHA-256 seguro e compacto do token para chaveamento em memória.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return token;
        }
    }
}
