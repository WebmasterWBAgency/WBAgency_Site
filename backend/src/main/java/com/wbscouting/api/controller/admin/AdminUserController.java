package com.wbscouting.api.controller.admin;

import com.wbscouting.api.dto.admin.AdminUserResponseDto;
import com.wbscouting.api.dto.admin.CreateAdminUserRequestDto;
import com.wbscouting.api.dto.admin.CreateAdminUserResponseDto;
import com.wbscouting.api.dto.admin.UpdateAdminRoleRequestDto;
import com.wbscouting.api.service.admin.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.wbscouting.api.security.audit.AuditAction;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/users", "/admin/users"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<Page<AdminUserResponseDto>> listUsers(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<AdminUserResponseDto> users = adminUserService.listUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @AuditAction(action = "CREATE", resource = "ADMIN_USER", description = "Criação de novo administrador secundário")
    public ResponseEntity<CreateAdminUserResponseDto> createUser(
            @Valid @RequestBody CreateAdminUserRequestDto request) {
        CreateAdminUserResponseDto response = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String resolveEmail(java.security.Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "";
    }

    @PatchMapping("/{id}/toggle-status")
    @AuditAction(action = "TOGGLE_STATUS", resource = "ADMIN_USER", description = "Ativação/Desativação de administrador")
    public ResponseEntity<AdminUserResponseDto> toggleUserStatus(
            @PathVariable UUID id,
            java.security.Principal principal) {
        String email = resolveEmail(principal);
        AdminUserResponseDto response = adminUserService.toggleUserStatus(id, email);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('WEBMASTER')")
    @AuditAction(action = "ADMIN_ROLE_UPDATED", resource = "ADMIN_USER", description = "Alteração de papel de administrador")
    public ResponseEntity<AdminUserResponseDto> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminRoleRequestDto request,
            java.security.Principal principal) {
        String email = resolveEmail(principal);
        AdminUserResponseDto response = adminUserService.updateRole(id, request.getRole(), email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reset-2fa")
    @PreAuthorize("hasRole('WEBMASTER')")
    @AuditAction(action = "RESET_2FA", resource = "ADMIN_USER", description = "Reset administrativo de 2FA de administrador")
    public ResponseEntity<java.util.Map<String, String>> reset2fa(
            @PathVariable UUID id,
            java.security.Principal principal) {
        String email = resolveEmail(principal);
        adminUserService.resetUserTwoFactor(id, email);
        return ResponseEntity.ok(java.util.Map.of("message", "Autenticação em duas etapas (2FA) desativada com sucesso."));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('WEBMASTER')")
    @AuditAction(action = "RESET_PASSWORD", resource = "ADMIN_USER", description = "Reset administrativo de senha de administrador")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
            @PathVariable UUID id,
            @RequestBody(required = false) com.wbscouting.api.dto.admin.AdminResetPasswordRequestDto request,
            java.security.Principal principal) {
        String email = resolveEmail(principal);
        String tempPassword = (request != null) ? request.getTemporaryPassword() : null;
        String effectivePassword = adminUserService.adminResetPassword(id, tempPassword, email);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Senha resetada com sucesso.",
                "temporaryPassword", effectivePassword
        ));
    }

    @DeleteMapping("/{id}")
    @AuditAction(action = "DELETE", resource = "ADMIN_USER", description = "Exclusão permanente de administrador secundário")
    public ResponseEntity<Void> deleteAdmin(
            @PathVariable UUID id,
            java.security.Principal principal) {
        String email = resolveEmail(principal);
        adminUserService.deleteSecondaryAdmin(id, email);
        return ResponseEntity.noContent().build();
    }
}
