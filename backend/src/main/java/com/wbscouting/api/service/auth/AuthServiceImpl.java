package com.wbscouting.api.service.auth;

import com.wbscouting.api.dto.AuthDTO;
import com.wbscouting.api.dto.auth.ForgotPasswordRequestDto;
import com.wbscouting.api.dto.auth.LoginRequestDto;
import com.wbscouting.api.dto.auth.LoginResponseDto;
import com.wbscouting.api.dto.auth.ResetPasswordRequestDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.entity.AdminLoginHistory;
import com.wbscouting.api.exception.InvalidTokenException;
import com.wbscouting.api.repository.AdminLoginHistoryRepository;
import com.wbscouting.api.repository.AdminRepository;
import com.wbscouting.api.security.JwtService;
import com.wbscouting.api.security.TokenHashUtils;
import com.wbscouting.api.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AdminRepository adminRepository;
    /** Repositorio de auditoria historica dos acessos administrativos. */
    private final AdminLoginHistoryRepository adminLoginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final com.wbscouting.api.security.TokenBlacklistService tokenBlacklistService;
    private final TotpService totpService;
    private final com.wbscouting.api.service.audit.AuditLogService auditLogService;

    /**
     * Persiste o historico de login em admin_login_history.<br>
     * <strong>FAIL-SAFE</strong>: qualquer falha de IO/DB na tabela de auditoria
     * NUNCA cancela ou quebra a autenticacao do usuario (principio de
     * disponibilidade do servico de autenticacao; log WARN eh gravado no slf4j).
     */
    private void recordLoginHistoryFailSafe(Admin admin, String clientIp, String userAgent) {
        if (admin == null || admin.getId() == null) {
            return;
        }
        try {
            adminLoginHistoryRepository.save(
                    AdminLoginHistory.builder()
                            .adminId(admin.getId())
                            .ipAddress(clientIp)
                            .userAgent(truncateUserAgent(userAgent))
                            .loggedAt(java.time.OffsetDateTime.now())
                            .build()
            );
        } catch (Exception ex) {
            // FAIL-SAFE: Nunca deixa auditoria quebrar login.
            log.warn("[AUTH-HISTORY] Falha ao gravar historico de login para admin '{}' (FAIL-SAFE: login nao foi interrompido). Causa: {}",
                    admin.getEmail(), ex.getMessage());
        }
    }

    /** Anti-DoS: User-Agents acima de 2000 caracteres nao sao persistidos integralmente. */
    private static String truncateUserAgent(String userAgent) {
        if (userAgent == null) return null;
        return userAgent.length() > 2000 ? userAgent.substring(0, 2000) : userAgent;
    }

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto request, String clientIp, String userAgent) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";

        Admin admin = adminRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> {
                    log.warn("Tentativa de login com e-mail não encontrado ou inativo: {}", email);
                    return new BadCredentialsException("Credenciais inválidas. Verifique seu e-mail e senha.");
                });

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            log.warn("Tentativa de login com senha incorreta para: {}", email);
            throw new BadCredentialsException("Credenciais inválidas. Verifique seu e-mail e senha.");
        }

        if (Boolean.TRUE.equals(admin.getIs2faEnabled())) {
            log.info("Desafio 2FA solicitado para: {}", admin.getEmail());
            String tempToken = jwtService.generate2faChallengeToken(admin);
            return LoginResponseDto.builder()
                    .requires2fa(true)
                    .tempToken(tempToken)
                    .adminEmail(admin.getEmail())
                    .build();
        }

        admin.setLastLoginAt(OffsetDateTime.now());
        adminRepository.save(admin);

        // 🔥 Persiste historico completo de login (IP + User-Agent) em admin_login_history.
        //    FAIL-SAFE: se tabela de auditoria falhar, login continua sem historico (log warn apenas).
        recordLoginHistoryFailSafe(admin, clientIp, userAgent);

        String token = jwtService.generateToken(admin);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                admin, null, admin.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("Administrador autenticado com sucesso: {}", admin.getEmail());

        return LoginResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .adminName(admin.getName())
                .adminEmail(admin.getEmail())
                .role(admin.getRole() != null ? admin.getRole().name() : null)
                .mustChangePassword(Boolean.TRUE.equals(admin.getMustChangePassword()))
                .requires2fa(false)
                .build();
    }

    @Override
    @Transactional
    public LoginResponseDto challenge2fa(com.wbscouting.api.dto.auth.TwoFactorChallengeRequestDto request, String clientIp, String userAgent) {
        String email;
        try {
            email = jwtService.extract2faChallengeEmail(request.getTempToken());
        } catch (Exception e) {
            log.warn("Token de desafio 2FA inválido ou expirado: {}", e.getMessage());
            throw new BadCredentialsException("Sessão de desafio 2FA expirada ou inválida. Faça login novamente.");
        }

        Admin admin = adminRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new BadCredentialsException("Administrador não encontrado ou inativo."));

        if (!Boolean.TRUE.equals(admin.getIs2faEnabled()) || admin.getTotpSecret() == null) {
            throw new BadCredentialsException("2FA não está habilitado para este usuário.");
        }

        String code = request.getCode().trim();
        boolean valid = totpService.verifyCode(admin.getTotpSecret(), code);

        if (!valid && admin.getBackupCodes() != null && !admin.getBackupCodes().isEmpty()) {
            java.util.List<String> backupCodes = new java.util.ArrayList<>(admin.getBackupCodes());
            String matchedCode = null;
            for (String storedHashed : backupCodes) {
                if (totpService.verifyBackupCode(code, storedHashed)) {
                    matchedCode = storedHashed;
                    break;
                }
            }
            if (matchedCode != null) {
                valid = true;
                backupCodes.remove(matchedCode);
                admin.setBackupCodes(backupCodes);
                log.info("Código de contingência (backup) 2FA utilizado por: {}", admin.getEmail());
            }
        }

        if (!valid) {
            log.warn("Código 2FA incorreto para o usuário: {}", admin.getEmail());
            throw new BadCredentialsException("Código de autenticação ou de contingência inválido.");
        }

        admin.setLastLoginAt(OffsetDateTime.now());
        adminRepository.save(admin);

        // 🔥 Persiste historico COMPLETO de login TAMBEM no sucesso do desafio 2FA
        //    (o primeiro passo (login com senha) soh gera LASTLOGINAT parcial em Admin,
        //     por isso o historico final eh gravado apenas quando o token de acesso
        //     eh efetivamente emitido).
        recordLoginHistoryFailSafe(admin, clientIp, userAgent);

        String token = jwtService.generateToken(admin);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                admin, null, admin.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("Administrador autenticado com 2FA com sucesso: {}", admin.getEmail());

        return LoginResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .adminName(admin.getName())
                .adminEmail(admin.getEmail())
                .role(admin.getRole() != null ? admin.getRole().name() : null)
                .mustChangePassword(Boolean.TRUE.equals(admin.getMustChangePassword()))
                .requires2fa(false)
                .build();
    }

    @Override
    @Transactional
    public void processForgotPassword(ForgotPasswordRequestDto request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        Optional<Admin> optionalAdmin = adminRepository.findByEmailAndIsActiveTrue(email);

        if (optionalAdmin.isPresent()) {
            Admin admin = optionalAdmin.get();
            // Gere um token seguro (UUID.randomUUID().toString())
            String rawResetToken = java.util.UUID.randomUUID().toString();
            // Salve password_reset_token_hash = TokenHashUtils.sha256(token)
            String hashedToken = TokenHashUtils.sha256(rawResetToken);

            admin.setPasswordResetTokenHash(hashedToken);
            admin.setPasswordResetExpiresAt(OffsetDateTime.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)); // 15 minutos de validade
            adminRepository.save(admin);

            // Dispare emailService.sendPasswordResetEmail(admin.getEmail(), admin.getName(), rawResetToken)
            emailService.sendPasswordResetEmail(admin.getEmail(), admin.getName(), rawResetToken);

            // Registre em auditoria (AUTH_PASSWORD_RESET_REQUESTED)
            if (auditLogService != null) {
                try {
                    auditLogService.recordLogAsync(
                            admin.getId(),
                            admin.getEmail(),
                            "AUTH_PASSWORD_RESET_REQUESTED",
                            "AUTH",
                            admin.getId() != null ? admin.getId().toString() : null,
                            "Solicitação de redefinição de senha com token de 15 minutos de validade",
                            java.util.Map.of("email", admin.getEmail()),
                            null,
                            null
                    );
                } catch (Exception ex) {
                    log.warn("Falha ao registrar auditoria de solicitação de reset de senha: {}", ex.getMessage());
                }
            }
            log.info("Token de recuperação de senha gerado e enviado para admin ID: {}", admin.getId());
        } else {
            log.info("Solicitação de recuperação de senha para e-mail não cadastrado ou inativo: {}", email);
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDto request) {
        String rawToken = request.getToken() != null ? request.getToken().trim() : "";
        if (rawToken.isBlank()) {
            throw new InvalidTokenException("Token de recuperação inválido ou expirado.");
        }

        Admin admin;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase();
            admin = adminRepository.findByEmailAndIsActiveTrue(email)
                    .orElseThrow(() -> new InvalidTokenException("Token de recuperação inválido ou expirado."));
        } else {
            // Fallback para requisições sem e-mail explícito
            String hashedToken = TokenHashUtils.sha256(rawToken);
            admin = adminRepository.findByPasswordResetToken(hashedToken)
                    .orElseThrow(() -> new InvalidTokenException("Token de recuperação inválido ou expirado."));
        }

        if (admin.getPasswordResetTokenHash() == null ||
                !TokenHashUtils.constantTimeVerify(rawToken, admin.getPasswordResetTokenHash())) {
            log.warn("Falha na validação constante de tempo do token de reset para admin ID: {}", admin.getId());
            throw new InvalidTokenException("Token de recuperação inválido ou expirado.");
        }

        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            log.warn("Tentativa de redefinição de senha para administrador inativo: {}", admin.getEmail());
            throw new InvalidTokenException("Token de recuperação inválido ou expirado.");
        }

        if (admin.getPasswordResetExpiresAt() == null || admin.getPasswordResetExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("Tentativa de redefinição com token expirado para admin ID: {}", admin.getId());
            throw new InvalidTokenException("Token de recuperação inválido ou expirado.");
        }

        if (request.getConfirmPassword() != null && !request.getConfirmPassword().isBlank()) {
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                throw new IllegalArgumentException("As senhas não coincidem.");
            }
        }

        validatePasswordComplexity(request.getNewPassword());

        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        admin.setPasswordResetTokenHash(null);
        admin.setPasswordResetExpiresAt(null);
        admin.setMustChangePassword(false);
        adminRepository.save(admin);

        // Invalide todas as sessões anteriores via tokenBlacklistService.revokeAllUserTokens(admin.getId())
        if (tokenBlacklistService != null) {
            tokenBlacklistService.revokeAllUserTokens(admin.getId());
            if (admin.getEmail() != null) {
                tokenBlacklistService.revokeAllUserTokens(admin.getEmail());
            }
        }

        // Registre auditoria (AUTH_PASSWORD_RESET_COMPLETED)
        if (auditLogService != null) {
            try {
                auditLogService.recordLogAsync(
                        admin.getId(),
                        admin.getEmail(),
                        "AUTH_PASSWORD_RESET_COMPLETED",
                        "AUTH",
                        admin.getId() != null ? admin.getId().toString() : null,
                        "Senha administrativa redefinida com sucesso pelo usuário",
                        java.util.Map.of("email", admin.getEmail()),
                        null,
                        null
                );
            } catch (Exception ex) {
                log.warn("Falha ao registrar auditoria de redefinição de senha: {}", ex.getMessage());
            }
        }

        log.info("Senha redefinida com sucesso para o administrador ID: {}", admin.getId());
    }

    @Override
    @Transactional
    public void activateAccount(com.wbscouting.api.dto.auth.ActivateAccountRequestDto request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String rawToken = request.getToken() != null ? request.getToken().trim() : "";

        Admin admin = adminRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new InvalidTokenException("Link de ativação inválido ou administrador inativo."));

        if (admin.getPasswordResetTokenHash() == null ||
                !TokenHashUtils.constantTimeVerify(rawToken, admin.getPasswordResetTokenHash())) {
            log.warn("Falha na validação do token de ativação para admin: {}", email);
            throw new InvalidTokenException("O link de ativação é inválido ou expirou.");
        }

        if (admin.getPasswordResetExpiresAt() == null || admin.getPasswordResetExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("Tentativa de ativação com token expirado para admin: {}", email);
            throw new InvalidTokenException("O link de ativação expirou. Solicite um novo acesso ao administrador.");
        }

        if (request.getConfirmPassword() != null && !request.getConfirmPassword().isBlank()) {
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                throw new IllegalArgumentException("As senhas não coincidem.");
            }
        }

        validatePasswordComplexity(request.getNewPassword());

        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        admin.setPasswordResetTokenHash(null);
        admin.setPasswordResetExpiresAt(null);
        admin.setMustChangePassword(false);
        adminRepository.save(admin);

        if (auditLogService != null) {
            try {
                auditLogService.recordLogAsync(
                        admin.getId(),
                        admin.getEmail(),
                        "ACTIVATE_ACCOUNT",
                        "AUTH",
                        admin.getId() != null ? admin.getId().toString() : null,
                        "Ativação de conta e primeiro acesso com definição de senha definitiva",
                        java.util.Map.of("email", admin.getEmail(), "role", admin.getRole() != null ? admin.getRole().name() : ""),
                        null,
                        null
                );
            } catch (Exception ex) {
                log.warn("Falha ao registrar auditoria de ativação de conta: {}", ex.getMessage());
            }
        }

        log.info("Conta ativada e senha definitiva cadastrada com sucesso para: {}", admin.getEmail());
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 8 caracteres.");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasSpecialOrDigit = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else hasSpecialOrDigit = true;
        }

        if (!hasUpper || !hasLower || !hasSpecialOrDigit) {
            throw new IllegalArgumentException("A senha deve conter letras maiúsculas, minúsculas e pelo menos um dígito ou caractere especial.");
        }
    }

    @Override
    @Transactional
    public AuthDTO.MessageResponse forgotPassword(AuthDTO.ForgotPasswordRequest request) {
        processForgotPassword(new ForgotPasswordRequestDto(request.getEmail()));
        return new AuthDTO.MessageResponse(
                "Se o e-mail informado estiver cadastrado em nosso sistema, as instruções para redefinição de senha serão enviadas em instantes."
        );
    }

    @Override
    @Transactional
    public AuthDTO.MessageResponse resetPassword(AuthDTO.ResetPasswordRequest request) {
        resetPassword(new ResetPasswordRequestDto(request.getToken(), request.getNewPassword()));
        return new AuthDTO.MessageResponse("Senha redefinida com sucesso.");
    }

    @Override
    public void logout(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String bearerToken = request.getHeader("Authorization");
        if (org.springframework.util.StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7).trim();
            try {
                java.util.Date expiration = jwtService.extractExpiration(token);
                tokenBlacklistService.blacklistToken(token, expiration);
            } catch (Exception ex) {
                tokenBlacklistService.blacklistToken(token, null);
            }
        }

        // Limpeza de cookies de sessão / refresh token
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (cookie.getName().equalsIgnoreCase("refreshToken") ||
                        cookie.getName().equalsIgnoreCase("wb_refresh_token")) {
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    cookie.setHttpOnly(true);
                    response.addCookie(cookie);
                }
            }
        }

        org.springframework.http.ResponseCookie deleteCookie = org.springframework.http.ResponseCookie.from("wb_refresh_token", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, deleteCookie.toString());

        log.info("Sessão administrativa encerrada e credenciais revogadas com sucesso (Hard Logout).");
    }

    @Override
    @Transactional
    public com.wbscouting.api.dto.auth.TwoFactorSetupResponseDto setupTwoFactor(java.util.UUID userId) {
        Admin admin = adminRepository.findById(userId)
                .orElseThrow(() -> new com.wbscouting.api.exception.ResourceNotFoundException("Administrador não encontrado com o ID: " + userId));
        return performSetupTwoFactor(admin);
    }

    @Override
    @Transactional
    public com.wbscouting.api.dto.auth.TwoFactorSetupResponseDto setupTwoFactor(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase(java.util.Locale.ROOT) : "";
        Admin admin = adminRepository.findByEmailAndIsActiveTrue(cleanEmail)
                .orElseThrow(() -> new com.wbscouting.api.exception.ResourceNotFoundException("Administrador não encontrado ou inativo: " + cleanEmail));
        return performSetupTwoFactor(admin);
    }

    private com.wbscouting.api.dto.auth.TwoFactorSetupResponseDto performSetupTwoFactor(Admin admin) {
        String secret = totpService.generateSecret();
        admin.setTotpSecret(secret);
        admin.setIs2faEnabled(false);
        adminRepository.save(admin);

        String otpauthUrl = totpService.getOtpauthUrl(admin.getEmail(), secret);
        String qrCodeDataUrl = totpService.generateQrCodeDataUrl(otpauthUrl);

        log.info("Setup de 2FA gerado para o administrador ID: {} ({})", admin.getId(), admin.getEmail());

        return com.wbscouting.api.dto.auth.TwoFactorSetupResponseDto.builder()
                .secret(secret)
                .otpauthUrl(otpauthUrl)
                .qrCodeDataUrl(qrCodeDataUrl)
                .qrCodeUri(otpauthUrl)
                .build();
    }

    @Override
    @Transactional
    public void confirmTwoFactor(java.util.UUID userId, String code) {
        Admin admin = adminRepository.findById(userId)
                .orElseThrow(() -> new com.wbscouting.api.exception.ResourceNotFoundException("Administrador não encontrado com o ID: " + userId));
        performConfirmTwoFactor(admin, code);
    }

    @Override
    @Transactional
    public void confirmTwoFactor(String email, String code) {
        String cleanEmail = email != null ? email.trim().toLowerCase(java.util.Locale.ROOT) : "";
        Admin admin = adminRepository.findByEmailAndIsActiveTrue(cleanEmail)
                .orElseThrow(() -> new com.wbscouting.api.exception.ResourceNotFoundException("Administrador não encontrado ou inativo: " + cleanEmail));
        performConfirmTwoFactor(admin, code);
    }

    private void performConfirmTwoFactor(Admin admin, String code) {
        if (admin.getTotpSecret() == null || admin.getTotpSecret().isBlank()) {
            throw new com.wbscouting.api.exception.BusinessException("O setup de 2FA ainda não foi iniciado.");
        }

        if (code == null || code.isBlank() || !totpService.verifyCode(admin.getTotpSecret(), code.trim())) {
            throw new com.wbscouting.api.exception.BusinessException("Código de autenticação inválido.");
        }

        java.util.List<String> rawBackupCodes = totpService.generateBackupCodes(8);
        java.util.List<String> hashedBackupCodes = rawBackupCodes.stream()
                .map(totpService::hashBackupCode)
                .toList();

        admin.setIs2faEnabled(true);
        admin.setBackupCodes(hashedBackupCodes);
        adminRepository.save(admin);

        auditLogService.recordLogAsync(
                admin.getId(),
                admin.getEmail(),
                "2FA_ENABLE",
                "ADMIN_USER",
                admin.getId().toString(),
                "Autenticação em duas etapas (2FA/TOTP) ativada com sucesso pelo usuário",
                java.util.Map.of("email", admin.getEmail()),
                null,
                null
        );

        log.info("2FA ativado com sucesso para o administrador ID: {} ({})", admin.getId(), admin.getEmail());
    }

    @Override
    @Transactional
    public void disableTwoFactor(java.util.UUID userId, String currentPassword) {
        Admin admin = adminRepository.findById(userId)
                .orElseThrow(() -> new com.wbscouting.api.exception.ResourceNotFoundException("Administrador não encontrado com o ID: " + userId));
        performDisableTwoFactor(admin, currentPassword);
    }

    @Override
    @Transactional
    public void disableTwoFactor(String email, String currentPassword) {
        String cleanEmail = email != null ? email.trim().toLowerCase(java.util.Locale.ROOT) : "";
        Admin admin = adminRepository.findByEmailAndIsActiveTrue(cleanEmail)
                .orElseThrow(() -> new com.wbscouting.api.exception.ResourceNotFoundException("Administrador não encontrado ou inativo: " + cleanEmail));
        performDisableTwoFactor(admin, currentPassword);
    }

    private void performDisableTwoFactor(Admin admin, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new BadCredentialsException("Senha atual informada está incorreta.");
        }

        admin.setIs2faEnabled(false);
        admin.setTotpSecret(null);
        admin.setBackupCodes(new java.util.ArrayList<>());
        adminRepository.save(admin);

        auditLogService.recordLogAsync(
                admin.getId(),
                admin.getEmail(),
                "2FA_DISABLE",
                "ADMIN_USER",
                admin.getId().toString(),
                "Autenticação em duas etapas (2FA/TOTP) desativada pelo usuário",
                java.util.Map.of("email", admin.getEmail()),
                null,
                null
        );

        log.info("2FA desativado pelo usuário para o administrador ID: {} ({})", admin.getId(), admin.getEmail());
    }
}
