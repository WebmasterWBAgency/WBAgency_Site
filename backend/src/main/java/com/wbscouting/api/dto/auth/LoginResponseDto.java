package com.wbscouting.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDto {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;

    private String adminName;

    private String adminEmail;

    private String role;

    @Builder.Default
    private Boolean requires2fa = false;

    @Builder.Default
    private Boolean requiresMfa = false;

    private String tempToken;

    private String tempChallengeToken;

    @Builder.Default
    private Boolean mustChangePassword = false;
}
