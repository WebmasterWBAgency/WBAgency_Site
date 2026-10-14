package com.wbscouting.api.security;

import com.wbscouting.api.security.logging.LogMaskingConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingConverterTest {

    @Test
    @DisplayName("Deve mascarar senha, CPF, telefone e email em mensagens JSON")
    void mask_JsonSensitiveData_Success() {
        String logMessage = "{\"email\": \"admin@wbscouting.com\", \"password\": \"SuperSecret#123\", \"phone\": \"11987654321\", \"cpf\": \"123.456.789-00\"}";

        String masked = LogMaskingConverter.mask(logMessage);

        assertThat(masked).doesNotContain("SuperSecret#123");
        assertThat(masked).doesNotContain("admin@wbscouting.com");
        assertThat(masked).doesNotContain("11987654321");
        assertThat(masked).doesNotContain("123.456.789-00");
        assertThat(masked).contains("\"password\": \"***MASKED***\"");
        assertThat(masked).contains("\"email\": \"***MASKED***\"");
        assertThat(masked).contains("\"phone\": \"***MASKED***\"");
        assertThat(masked).contains("\"cpf\": \"***MASKED***\"");
    }

    @Test
    @DisplayName("Deve mascarar token e segredo TOTP em pares chave-valor")
    void mask_KeyValueSensitiveData_Success() {
        String logMessage = "Request with token=eyJhbGciOiJIUzI1NiIsInR5cCI and totpSecret=JBSWY3DPEHPK3PXP and password=AdminPass123";

        String masked = LogMaskingConverter.mask(logMessage);

        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI");
        assertThat(masked).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(masked).doesNotContain("AdminPass123");
        assertThat(masked).contains("token=***MASKED***");
        assertThat(masked).contains("totpSecret=***MASKED***");
        assertThat(masked).contains("password=***MASKED***");
    }

    @Test
    @DisplayName("Deve mascarar CPF formatado em texto corrido")
    void mask_FormattedCpf_Success() {
        String logMessage = "Candidato cadastrado com documento 321.654.987-11 no sistema.";

        String masked = LogMaskingConverter.mask(logMessage);

        assertThat(masked).doesNotContain("321.654.987-11");
        assertThat(masked).contains("***MASKED***");
    }
}
