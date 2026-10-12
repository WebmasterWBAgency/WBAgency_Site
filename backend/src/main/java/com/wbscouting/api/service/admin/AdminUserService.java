package com.wbscouting.api.service.admin;

import com.wbscouting.api.dto.admin.AdminUserResponseDto;
import com.wbscouting.api.dto.admin.CreateAdminUserRequestDto;
import com.wbscouting.api.dto.admin.CreateAdminUserResponseDto;
import com.wbscouting.api.enums.AdminRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {

    Page<AdminUserResponseDto> listUsers(Pageable pageable);

    CreateAdminUserResponseDto createUser(CreateAdminUserRequestDto request);

    default CreateAdminUserResponseDto createAdminUser(CreateAdminUserRequestDto request) {
        return createUser(request);
    }

    AdminUserResponseDto toggleUserStatus(UUID id, String authenticatedEmail);

    AdminUserResponseDto updateRole(UUID id, AdminRole newRole, String authenticatedEmail);

    default AdminUserResponseDto updateUserRole(UUID id, AdminRole newRole, String authenticatedEmail) {
        return updateRole(id, newRole, authenticatedEmail);
    }

    void resetUserTwoFactor(UUID targetId, String authenticatedEmail);

    default void resetUserTwoFactor(UUID targetId) {
        resetUserTwoFactor(targetId, null);
    }

    String adminResetPassword(UUID targetId, String temporaryPassword, String authenticatedEmail);

    default String adminResetPassword(UUID targetId, String temporaryPassword) {
        return adminResetPassword(targetId, temporaryPassword, null);
    }

    void deleteSecondaryAdmin(UUID id, String authenticatedEmail);
}
