package com.wbscouting.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorDisableRequestDto {

    @JsonAlias({"currentPassword", "current_password"})
    private String password;

    private String currentPassword;

    public String getEffectivePassword() {
        if (currentPassword != null && !currentPassword.isBlank()) {
            return currentPassword.trim();
        }
        if (password != null && !password.isBlank()) {
            return password.trim();
        }
        return "";
    }

    @AssertTrue(message = "A senha atual é obrigatória para desativar o 2FA.")
    public boolean isValid() {
        return !getEffectivePassword().isBlank();
    }
}
