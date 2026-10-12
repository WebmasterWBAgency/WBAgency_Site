package com.wbscouting.api.service.admin;

import com.wbscouting.api.dto.admin.ChangePasswordRequestDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.repository.AdminRepository;
import com.wbscouting.api.service.auth.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProfileServiceImplTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpService totpService;

    @InjectMocks
    private AdminProfileServiceImpl profileService;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = Admin.builder()
                .id(UUID.randomUUID())
                .name("Admin Tester")
                .email("admin@wbagency.com.br")
                .passwordHash("hashedCurrentPassword")
                .mustChangePassword(true)
                .build();
    }

    @Test
    @DisplayName("Deve alterar senha própria via UUID com sucesso e desmarcar mustChangePassword")
    void shouldChangePasswordByUserIdSuccessfully() {
        ChangePasswordRequestDto dto = ChangePasswordRequestDto.builder()
                .currentPassword("OldPassword123!")
                .newPassword("NewPassword456@")
                .confirmPassword("NewPassword456@")
                .build();

        when(adminRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("OldPassword123!", "hashedCurrentPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword456@")).thenReturn("newHashedPassword");

        profileService.changePassword(admin.getId(), dto);

        assertThat(admin.getPasswordHash()).isEqualTo("newHashedPassword");
        assertThat(admin.getMustChangePassword()).isFalse();
        verify(adminRepository).save(admin);
    }

    @Test
    @DisplayName("Deve rejeitar alteração se a senha atual estiver incorreta")
    void shouldRejectWhenCurrentPasswordIsIncorrect() {
        ChangePasswordRequestDto dto = ChangePasswordRequestDto.builder()
                .currentPassword("WrongPassword123!")
                .newPassword("NewPassword456@")
                .confirmPassword("NewPassword456@")
                .build();

        when(adminRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("WrongPassword123!", "hashedCurrentPassword")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(admin.getId(), dto))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("A senha atual informada está incorreta");

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar se a confirmação de senha não coincidir")
    void shouldRejectWhenConfirmationDoesNotMatch() {
        ChangePasswordRequestDto dto = ChangePasswordRequestDto.builder()
                .currentPassword("OldPassword123!")
                .newPassword("NewPassword456@")
                .confirmPassword("DifferentPassword456@")
                .build();

        when(adminRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("OldPassword123!", "hashedCurrentPassword")).thenReturn(true);

        assertThatThrownBy(() -> profileService.changePassword(admin.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmação da nova senha não confere");

        verify(adminRepository, never()).save(any());
    }
}
