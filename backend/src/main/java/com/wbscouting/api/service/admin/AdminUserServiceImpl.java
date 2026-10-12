package com.wbscouting.api.service.admin;

import com.wbscouting.api.dto.admin.AdminUserResponseDto;
import com.wbscouting.api.dto.admin.CreateAdminUserRequestDto;
import com.wbscouting.api.dto.admin.CreateAdminUserResponseDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.enums.AdminRole;
import com.wbscouting.api.exception.ResourceNotFoundException;
import com.wbscouting.api.repository.AdminLoginHistoryRepository;
import com.wbscouting.api.repository.AdminRepository;
import com.wbscouting.api.exception.BusinessException;
import com.wbscouting.api.security.TokenBlacklistService;
import com.wbscouting.api.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wbscouting.api.security.TokenHashUtils;
import com.wbscouting.api.service.email.EmailService;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminRepository adminRepository;
    private final AdminLoginHistoryRepository adminLoginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponseDto> listUsers(Pageable pageable) {
        return adminRepository.findAll(pageable).map(this::toResponseDto);
    }

    @Override
    @Transactional
    public CreateAdminUserResponseDto createUser(CreateAdminUserRequestDto request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (adminRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Já existe um administrador cadastrado com o e-mail: " + email);
        }

        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = UUID.randomUUID().toString();
        } else {
            validatePasswordComplexity(rawPassword);
        }

        // ============================================================
        // 🔥 REGRA DE GOVERNANÇA RBAC / ADM-018: Impedir criacao indevida de WEBMASTER.
        // Apenas o DataInitializer (se, em ambiente LOCAL/DEV) pode gerar o primeiro Webmaster.
        // Qualquer tentativa via endpoint admin-api cria usuarios Admin/Scouter. Promocoes
        // de Admin via /admin/users.
        // ============================================================
        AdminRole effectiveRole = request.getRole();
        if (effectiveRole == AdminRole.WEBMASTER) {
            log.warn("[ADM-018] Tentativa de CRIAR novo administrador com papel WEBMASTER bloqueada via API. " +
                    "Promovendo para SUPER_ADMIN (papel maximo permitido para criacao manual). Email-alvo: {}", email);
            effectiveRole = AdminRole.SUPER_ADMIN;
        }

        String activationToken = UUID.randomUUID().toString();
        String tokenHash = TokenHashUtils.sha256(activationToken);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(24, ChronoUnit.HOURS);

        Admin admin = Admin.builder()
                .name(request.getName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(effectiveRole)
                .isActive(true)
                .is2faEnabled(false)
                .mustChangePassword(true)
                .passwordResetTokenHash(tokenHash)
                .passwordResetExpiresAt(expiresAt)
                .build();

        Admin saved = adminRepository.save(admin);
        log.info("Novo administrador cadastrado com sucesso. ID: {}, E-mail: {}, Role: {}", saved.getId(), saved.getEmail(), saved.getRole());

        try {
            emailService.sendAdminWelcomeEmail(saved.getEmail(), saved.getName(), saved.getRole().name(), activationToken);
        } catch (Exception ex) {
            log.error("Falha ao despachar e-mail de boas-vindas para {}: {}", saved.getEmail(), ex.getMessage());
        }

        return CreateAdminUserResponseDto.builder()
                .user(toResponseDto(saved))
                .temporaryPassword(rawPassword)
                .build();
    }

    @Override
    @Transactional
    public AdminUserResponseDto toggleUserStatus(UUID id, String authenticatedEmail) {
        Admin target = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador não encontrado com o ID: " + id));

        if (target.getEmail().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new IllegalArgumentException("Operação bloqueada: Não é permitido desativar sua própria conta de administrador.");
        }

        // ============================================================
        // 🔥 REGRA DE SEGURANÇA ESTRITA: WEBMASTER nunca pode ser desativado/ativado.
        // O papel WEBMASTER e vinculado unicamente ao provisionamento inicial.
        // ============================================================
        if (target.getRole() == AdminRole.WEBMASTER) {
            throw new IllegalStateException(
                    "Operação bloqueada (RBAC-018): O papel de WEBMASTER é imutável e não pode ter seu status (ativo/inativo) alterado por meio do painel. " +
                    "Contate o suporte técnico caso precise ajustar acessos do Webmaster."
            );
        }

        boolean willDeactivate = Boolean.TRUE.equals(target.getIsActive());

        if (willDeactivate && isSuperRole(target.getRole())) {
            long activeSuperAdmins = adminRepository.countByRoleInAndIsActiveTrue(List.of(AdminRole.SUPER_ADMIN, AdminRole.WEBMASTER));
            if (activeSuperAdmins <= 1) {
                throw new IllegalStateException("Operação bloqueada: Não é permitido desativar o único Webmaster/SuperAdmin ativo no sistema.");
            }
        }

        target.setIsActive(!willDeactivate);
        Admin updated = adminRepository.save(target);
        log.info("Status do administrador ID: {} alterado para isActive={}", updated.getId(), updated.getIsActive());

        return toResponseDto(updated);
    }

    @Override
    @Transactional
    public AdminUserResponseDto updateRole(UUID id, AdminRole newRole, String authenticatedEmail) {
        Admin target = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador não encontrado com o ID: " + id));

        Admin currentUser = null;
        if (authenticatedEmail != null && !authenticatedEmail.isBlank()) {
            currentUser = adminRepository.findByEmail(authenticatedEmail.trim().toLowerCase(Locale.ROOT)).orElse(null);
        }

        // ============================================================
        // 🔥 REGRA: Promoção a WEBMASTER:
        // Se o novo papel for AdminRole.WEBMASTER:
        // Busque se já existe algum WEBMASTER ativo no banco (countByRole(AdminRole.WEBMASTER) >= 1).
        // Se existir e o ID desse Webmaster for diferente do ID do usuário em edição, lance:
        // BusinessException("Já existe um Webmaster ativo no sistema. É necessário rebaixar o atual antes de promover outro.").
        // ============================================================
        if (newRole == AdminRole.WEBMASTER) {
            if (adminRepository.countByRole(AdminRole.WEBMASTER) >= 1) {
                Optional<Admin> existingWebmaster = adminRepository.findByRole(AdminRole.WEBMASTER);
                if (existingWebmaster.isPresent() && !existingWebmaster.get().getId().equals(id)) {
                    throw new BusinessException("Já existe um Webmaster ativo no sistema. É necessário rebaixar o atual antes de promover outro.");
                } else if (existingWebmaster.isEmpty() && target.getRole() != AdminRole.WEBMASTER) {
                    throw new BusinessException("Já existe um Webmaster ativo no sistema. É necessário rebaixar o atual antes de promover outro.");
                }
            }
        }

        // ============================================================
        // 🔥 REGRA: Bloqueio de Auto-Rebaixamento:
        // Se o usuário autenticado (currentUser) for o WEBMASTER e estiver alterando o seu próprio papel
        // para uma role inferior (ex.: ADMIN ou SCOUT), verifique se ele é o único Webmaster.
        // Se for o único, lance BusinessException("O Webmaster não pode revogar o seu próprio cargo sem antes transferi-lo para outro administrador.").
        // ============================================================
        boolean isCurrentUserWebmaster = (currentUser != null && currentUser.getRole() == AdminRole.WEBMASTER)
                || (target.getRole() == AdminRole.WEBMASTER && authenticatedEmail != null && authenticatedEmail.equalsIgnoreCase(target.getEmail()));
        boolean isSelfEdit = (currentUser != null && currentUser.getId().equals(id))
                || (authenticatedEmail != null && authenticatedEmail.equalsIgnoreCase(target.getEmail()));

        if (isCurrentUserWebmaster && isSelfEdit && newRole != AdminRole.WEBMASTER) {
            long webmasterCount = adminRepository.countByRole(AdminRole.WEBMASTER);
            if (webmasterCount <= 1) {
                throw new BusinessException("O Webmaster não pode revogar o seu próprio cargo sem antes transferi-lo para outro administrador.");
            }
        }

        if (target.getEmail().equalsIgnoreCase(authenticatedEmail != null ? authenticatedEmail.trim() : "") && isSuperRole(target.getRole()) && !isSuperRole(newRole)) {
            throw new BusinessException("Operação bloqueada: Não é permitido rebaixar seu próprio papel de Webmaster.");
        }

        if (isSuperRole(target.getRole()) && !isSuperRole(newRole)) {
            long activeSuperAdmins = adminRepository.countByRoleInAndIsActiveTrue(List.of(AdminRole.SUPER_ADMIN, AdminRole.WEBMASTER));
            if (activeSuperAdmins <= 1) {
                throw new BusinessException("Operação bloqueada: Não é permitido rebaixar o único Webmaster/SuperAdmin ativo no sistema.");
            }
        }

        target.setRole(newRole);
        Admin updated = adminRepository.save(target);
        log.info("Papel do administrador ID: {} atualizado para {}", updated.getId(), updated.getRole());

        auditLogService.recordLogAsync(
                currentUser != null ? currentUser.getId() : null,
                authenticatedEmail,
                "ADMIN_ROLE_UPDATED",
                "ADMIN_USER",
                target.getId().toString(),
                "Papel do administrador " + target.getEmail() + " alterado para " + newRole,
                java.util.Map.of("targetEmail", target.getEmail(), "targetId", target.getId().toString(), "newRole", newRole.name()),
                null,
                null
        );

        return toResponseDto(updated);
    }

    @Override
    @Transactional
    public void resetUserTwoFactor(UUID targetId, String authenticatedEmail) {
        if (authenticatedEmail != null && !authenticatedEmail.isBlank()) {
            Admin currentUser = adminRepository.findByEmail(authenticatedEmail.trim().toLowerCase(Locale.ROOT)).orElse(null);
            if (currentUser != null && currentUser.getRole() != AdminRole.WEBMASTER) {
                throw new org.springframework.security.access.AccessDeniedException("Apenas o Webmaster pode resetar o 2FA de outros administradores.");
            }
        }

        Admin target = adminRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador não encontrado com o ID: " + targetId));

        target.setIs2faEnabled(false);
        target.setTotpSecret(null);
        target.setBackupCodes(new java.util.ArrayList<>());
        adminRepository.save(target);

        // Invalida tokens de sessão ativos via TokenBlacklistService
        tokenBlacklistService.revokeAllUserTokens(target.getEmail());
        tokenBlacklistService.revokeAllUserTokens(target.getId().toString());

        // Registra em log via AuditLogService (Action: ADMIN_2FA_RESET)
        auditLogService.recordLogAsync(
                null,
                authenticatedEmail,
                "ADMIN_2FA_RESET",
                "ADMIN_USER",
                target.getId().toString(),
                "Reset administrativo de 2FA efetuado para o administrador " + target.getEmail(),
                java.util.Map.of("targetEmail", target.getEmail(), "targetId", target.getId().toString()),
                null,
                null
        );

        log.info("2FA resetado com sucesso para o administrador ID: {} ({}) por {}", target.getId(), target.getEmail(), authenticatedEmail);
    }

    @Override
    @Transactional
    public String adminResetPassword(UUID targetId, String temporaryPassword, String authenticatedEmail) {
        Admin target = adminRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador não encontrado com o ID: " + targetId));

        String effectivePassword = temporaryPassword;
        if (effectivePassword == null || effectivePassword.isBlank()) {
            effectivePassword = generateSecureTemporaryPassword();
        } else {
            validatePasswordComplexity(effectivePassword);
        }

        target.setPasswordHash(passwordEncoder.encode(effectivePassword));
        target.setMustChangePassword(true);
        target.setPasswordResetTokenHash(null);
        target.setPasswordResetExpiresAt(null);
        adminRepository.save(target);

        // Invalida tokens de sessão ativos via TokenBlacklistService.java
        tokenBlacklistService.revokeAllUserTokens(target.getEmail());

        // Registra em log via AuditLogService.java
        auditLogService.recordLogAsync(
                null,
                authenticatedEmail,
                "RESET_PASSWORD",
                "ADMIN_USER",
                target.getId().toString(),
                "Reset administrativo de senha efetuado para o administrador " + target.getEmail(),
                java.util.Map.of("targetEmail", target.getEmail(), "targetId", target.getId().toString()),
                null,
                null
        );

        log.info("Senha resetada administrativamente para o administrador ID: {} ({}) por {}", target.getId(), target.getEmail(), authenticatedEmail);
        return effectivePassword;
    }

    @Override
    @Transactional
    public void deleteSecondaryAdmin(UUID id, String authenticatedEmail) {
        Admin target = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Administrador não encontrado com o ID: " + id));

        if (target.getEmail().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new IllegalArgumentException("Operação bloqueada: Não é permitido excluir sua própria conta de administrador.");
        }

        // ============================================================
        // 🔥 REGRA DE SEGURANÇA ESTRITA: WEBMASTER NUNCA PODE SER EXCLUIDO VIA API.
        // Exclusão de contas de provisionamento inicial.
        // ============================================================
        if (target.getRole() == AdminRole.WEBMASTER) {
            throw new IllegalStateException(
                    "Operação bloqueada (RBAC-018): Contas com papel de WEBMASTER são contas de provisionamento raiz e NÃO PODEM SER EXCLUÍDAS pelo painel admin. " +
                    "Remoção exige migração de banco gerenciada com auditoria externa."
            );
        }

        if (isSuperRole(target.getRole())) {
            long activeSuperAdmins = adminRepository.countByRoleInAndIsActiveTrue(List.of(AdminRole.SUPER_ADMIN, AdminRole.WEBMASTER));
            if (activeSuperAdmins <= 1) {
                throw new IllegalStateException("Operação bloqueada: Não é permitido excluir o único Webmaster/SuperAdmin ativo no sistema.");
            }
        }

        adminRepository.delete(target);
        log.info("Administrador ID: {} ({}) excluído com sucesso por {}", target.getId(), target.getEmail(), authenticatedEmail);
    }

    private boolean isSuperRole(AdminRole role) {
        return role == AdminRole.SUPER_ADMIN || role == AdminRole.WEBMASTER;
    }

    private String generateSecureTemporaryPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String special = "@#$%&*!";

        StringBuilder sb = new StringBuilder();
        sb.append("Wb@");
        for (int i = 0; i < 3; i++) {
            sb.append(upper.charAt(secureRandom.nextInt(upper.length())));
            sb.append(lower.charAt(secureRandom.nextInt(lower.length())));
        }
        sb.append(digits.charAt(secureRandom.nextInt(digits.length())));
        sb.append(special.charAt(secureRandom.nextInt(special.length())));
        return sb.toString();
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
            throw new IllegalArgumentException("A senha deve conter letras maiúsculas, minúsculas e pelo menos um número ou caractere especial.");
        }
    }

    private AdminUserResponseDto toResponseDto(Admin admin) {
        // ====== FALLBACK lastLoginAt: =========================
        // Se Admin.lastLoginAt for null (dados historicos antigos
        // antes da implementacao ou contas inativas antigas),
        // fazemos backfill de 1 unico registro na tabela de
        // auditoria de login historico, garantindo assim exibicao
        // correta no painel /admin/usuarios sem N+1 excessivo.
        // ======================================================
        java.time.OffsetDateTime lastLogin = admin.getLastLoginAt();
        if (lastLogin == null) {
            try {
                lastLogin = adminLoginHistoryRepository
                        .findLastLoginDateByAdminId(admin.getId())
                        .orElse(null);
            } catch (Exception ignored) {
                // FAIL-SAFE: se tabela nao existir ainda (H2 dev inicial)
                // ou houver erro de FK, mantem null para exibir
                // "Nunca acessou" no frontend.
                lastLogin = null;
            }
        }

        return AdminUserResponseDto.builder()
                .id(admin.getId())
                .name(admin.getName())
                .email(admin.getEmail())
                .role(admin.getRole())
                .isActive(admin.getIsActive())
                .is2faEnabled(admin.getIs2faEnabled())
                .mustChangePassword(admin.getMustChangePassword())
                .lastLoginAt(lastLogin)
                .createdAt(admin.getCreatedAt())
                .build();
    }
}
