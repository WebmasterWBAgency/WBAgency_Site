package com.wbscouting.api.service.admin;

import com.wbscouting.api.dto.admin.AdminUserResponseDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.enums.AdminRole;
import com.wbscouting.api.exception.BusinessException;
import com.wbscouting.api.repository.AdminLoginHistoryRepository;
import com.wbscouting.api.repository.AdminRepository;
import com.wbscouting.api.security.TokenBlacklistService;
import com.wbscouting.api.service.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AdminLoginHistoryRepository adminLoginHistoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.wbscouting.api.service.email.EmailService emailService;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private Admin webmaster;
    private Admin secondaryAdmin;

    @BeforeEach
    void setUp() {
        webmaster = Admin.builder()
                .id(UUID.randomUUID())
                .name("Webmaster WB Agency")
                .email("webmaster@wbagency.com.br")
                .role(AdminRole.WEBMASTER)
                .isActive(true)
                .build();

        secondaryAdmin = Admin.builder()
                .id(UUID.randomUUID())
                .name("Admin Secundário")
                .email("info@wbagency.com.br")
                .role(AdminRole.ADMIN)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Deve bloquear auto-desativação do Webmaster logado")
    void shouldBlockSelfDeactivation() {
        when(adminRepository.findById(webmaster.getId())).thenReturn(Optional.of(webmaster));

        assertThatThrownBy(() -> adminUserService.toggleUserStatus(webmaster.getId(), "webmaster@wbagency.com.br"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Não é permitido desativar sua própria conta");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear desativação quando for o único Webmaster/SuperAdmin ativo")
    void shouldBlockDeactivationWhenOnlyOneSuperAdminRemains() {
        Admin otherWebmaster = Admin.builder()
                .id(UUID.randomUUID())
                .name("Outro SuperAdmin")
                .email("outro@wbagency.com.br")
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        when(adminRepository.findById(otherWebmaster.getId())).thenReturn(Optional.of(otherWebmaster));
        when(adminRepository.countByRoleInAndIsActiveTrue(anyList())).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.toggleUserStatus(otherWebmaster.getId(), "webmaster@wbagency.com.br"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("único Webmaster/SuperAdmin ativo");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear rebaixamento do próprio papel de Webmaster")
    void shouldBlockSelfDemotion() {
        Admin superAdmin = Admin.builder()
                .id(UUID.randomUUID())
                .name("Super Admin")
                .email("super@wbagency.com.br")
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        when(adminRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> adminUserService.updateRole(superAdmin.getId(), AdminRole.ADMIN, "super@wbagency.com.br"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Não é permitido rebaixar seu próprio papel");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear auto-rebaixamento do Webmaster titular via checagem currentUser.getId()")
    void shouldBlockWebmasterSelfDemotionByUserId() {
        when(adminRepository.findById(webmaster.getId())).thenReturn(Optional.of(webmaster));
        when(adminRepository.findByEmail("webmaster@wbagency.com.br")).thenReturn(Optional.of(webmaster));
        when(adminRepository.countByRole(AdminRole.WEBMASTER)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.updateRole(webmaster.getId(), AdminRole.ADMIN, "webmaster@wbagency.com.br"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("O Webmaster não pode revogar o seu próprio cargo sem antes transferi-lo para outro administrador.");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear promoção para WEBMASTER quando já existe um Webmaster ativo")
    void shouldBlockPromotionToWebmasterWhenOneAlreadyExists() {
        when(adminRepository.findById(secondaryAdmin.getId())).thenReturn(Optional.of(secondaryAdmin));
        when(adminRepository.countByRole(AdminRole.WEBMASTER)).thenReturn(1L);
        when(adminRepository.findByRole(AdminRole.WEBMASTER)).thenReturn(Optional.of(webmaster));

        assertThatThrownBy(() -> adminUserService.updateRole(secondaryAdmin.getId(), AdminRole.WEBMASTER, "webmaster@wbagency.com.br"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um Webmaster ativo no sistema. É necessário rebaixar o atual antes de promover outro.");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve permitir promoção para WEBMASTER quando não existe nenhum ativo")
    void shouldAllowPromotionToWebmasterWhenNoneExists() {
        when(adminRepository.findById(secondaryAdmin.getId())).thenReturn(Optional.of(secondaryAdmin));
        when(adminRepository.countByRole(AdminRole.WEBMASTER)).thenReturn(0L);
        when(adminRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponseDto result = adminUserService.updateRole(secondaryAdmin.getId(), AdminRole.WEBMASTER, "webmaster@wbagency.com.br");

        assertThat(result.getRole()).isEqualTo(AdminRole.WEBMASTER);
        verify(adminRepository).save(any());
        verify(auditLogService).recordLogAsync(any(), eq("webmaster@wbagency.com.br"), eq("ADMIN_ROLE_UPDATED"), eq("ADMIN_USER"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve resetar 2FA com sucesso invalidando sessões e registrando auditoria")
    void shouldResetUserTwoFactorSuccessfully() {
        secondaryAdmin.setIs2faEnabled(true);
        secondaryAdmin.setTotpSecret("SECRET");
        when(adminRepository.findByEmail("webmaster@wbagency.com.br")).thenReturn(Optional.of(webmaster));
        when(adminRepository.findById(secondaryAdmin.getId())).thenReturn(Optional.of(secondaryAdmin));

        adminUserService.resetUserTwoFactor(secondaryAdmin.getId(), "webmaster@wbagency.com.br");

        assertThat(secondaryAdmin.getIs2faEnabled()).isFalse();
        assertThat(secondaryAdmin.getTotpSecret()).isNull();
        verify(tokenBlacklistService).revokeAllUserTokens(secondaryAdmin.getEmail());
        verify(auditLogService).recordLogAsync(any(), eq("webmaster@wbagency.com.br"), eq("ADMIN_2FA_RESET"), eq("ADMIN_USER"), any(), any(), any(), any(), any());
        verify(adminRepository).save(secondaryAdmin);
    }

    @Test
    @DisplayName("Deve bloquear reset de 2FA se o operador não for Webmaster")
    void shouldRejectReset2FaWhenNotWebmaster() {
        Admin regularAdmin = Admin.builder()
                .id(UUID.randomUUID())
                .email("admin@wbagency.com.br")
                .role(com.wbscouting.api.enums.AdminRole.ADMIN)
                .build();
        when(adminRepository.findByEmail("admin@wbagency.com.br")).thenReturn(Optional.of(regularAdmin));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adminUserService.resetUserTwoFactor(secondaryAdmin.getId(), "admin@wbagency.com.br")
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("Deve resetar senha de usuário administrativamente com sucesso")
    void shouldAdminResetPasswordSuccessfully() {
        when(adminRepository.findById(secondaryAdmin.getId())).thenReturn(Optional.of(secondaryAdmin));
        when(passwordEncoder.encode(any())).thenReturn("encodedHash");

        String tempPwd = adminUserService.adminResetPassword(secondaryAdmin.getId(), "Temp123!@#", "webmaster@wbagency.com.br");

        assertThat(tempPwd).isEqualTo("Temp123!@#");
        assertThat(secondaryAdmin.getMustChangePassword()).isTrue();
        assertThat(secondaryAdmin.getPasswordHash()).isEqualTo("encodedHash");
        verify(tokenBlacklistService).revokeAllUserTokens(secondaryAdmin.getEmail());
        verify(auditLogService).recordLogAsync(any(), eq("webmaster@wbagency.com.br"), eq("RESET_PASSWORD"), eq("ADMIN_USER"), any(), any(), any(), any(), any());
        verify(adminRepository).save(secondaryAdmin);
    }

    @Test
    @DisplayName("Deve bloquear rebaixamento caso reste apenas um Webmaster/SuperAdmin ativo")
    void shouldBlockDemotionWhenOnlyOneSuperAdminRemains() {
        Admin otherWebmaster = Admin.builder()
                .id(UUID.randomUUID())
                .name("Outro Webmaster")
                .email("outro@wbagency.com.br")
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        when(adminRepository.findById(otherWebmaster.getId())).thenReturn(Optional.of(otherWebmaster));
        when(adminRepository.countByRoleInAndIsActiveTrue(anyList())).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.updateRole(otherWebmaster.getId(), AdminRole.SCOUT, "webmaster@wbagency.com.br"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("único Webmaster/SuperAdmin ativo");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear auto-exclusão do Webmaster logado")
    void shouldBlockSelfDeletion() {
        when(adminRepository.findById(webmaster.getId())).thenReturn(Optional.of(webmaster));

        assertThatThrownBy(() -> adminUserService.deleteSecondaryAdmin(webmaster.getId(), "webmaster@wbagency.com.br"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Não é permitido excluir sua própria conta");

        verify(adminRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Deve bloquear exclusão caso reste apenas um Webmaster/SuperAdmin ativo")
    void shouldBlockDeletionWhenOnlyOneSuperAdminRemains() {
        Admin otherWebmaster = Admin.builder()
                .id(UUID.randomUUID())
                .name("Outro Webmaster")
                .email("outro@wbagency.com.br")
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        when(adminRepository.findById(otherWebmaster.getId())).thenReturn(Optional.of(otherWebmaster));
        when(adminRepository.countByRoleInAndIsActiveTrue(anyList())).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.deleteSecondaryAdmin(otherWebmaster.getId(), "webmaster@wbagency.com.br"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("único Webmaster/SuperAdmin ativo");

        verify(adminRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Deve permitir exclusão de administrador secundário com sucesso")
    void shouldAllowDeletionOfSecondaryAdmin() {
        when(adminRepository.findById(secondaryAdmin.getId())).thenReturn(Optional.of(secondaryAdmin));

        adminUserService.deleteSecondaryAdmin(secondaryAdmin.getId(), "webmaster@wbagency.com.br");

        verify(adminRepository, times(1)).delete(secondaryAdmin);
    }

    @Test
    @DisplayName("Deve criar novo administrador com mustChangePassword=true, token de ativação e despachar e-mail")
    void shouldCreateAdminWithActivationTokenAndMustChangePasswordAndSendEmail() {
        com.wbscouting.api.dto.admin.CreateAdminUserRequestDto request =
                com.wbscouting.api.dto.admin.CreateAdminUserRequestDto.builder()
                        .name("Novo Admin")
                        .email("novoadmin@wbagency.com.br")
                        .role(AdminRole.ADMIN)
                        .password("SenhaForte@2026")
                        .build();

        when(adminRepository.existsByEmail("novoadmin@wbagency.com.br")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pwd");
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> {
            Admin a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        com.wbscouting.api.dto.admin.CreateAdminUserResponseDto response = adminUserService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("novoadmin@wbagency.com.br");

        org.mockito.ArgumentCaptor<Admin> adminCaptor = org.mockito.ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(adminCaptor.capture());

        Admin captured = adminCaptor.getValue();
        assertThat(captured.getMustChangePassword()).isTrue();
        assertThat(captured.getPasswordResetTokenHash()).isNotNull();
        assertThat(captured.getPasswordResetExpiresAt()).isNotNull();

        verify(emailService, times(1)).sendAdminWelcomeEmail(
                eq("novoadmin@wbagency.com.br"),
                eq("Novo Admin"),
                eq("ADMIN"),
                anyString()
        );
    }
}
