package com.wbscouting.api.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    @DisplayName("Deve gerar segredo Base32 válido e montar URI otpauth")
    void shouldGenerateValidSecretAndOtpauthUrl() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length());

        String otpauthUrl = totpService.getOtpauthUrl("admin@wbagency.com.br", secret);
        assertTrue(otpauthUrl.startsWith("otpauth://totp/"));
        assertTrue(otpauthUrl.contains("secret=" + secret));
        assertTrue(otpauthUrl.contains("WBAgency"));
        assertTrue(otpauthUrl.contains("admin%40wbagency.com.br"));
    }

    @Test
    @DisplayName("Deve gerar QR Code em formato Base64 Data URL")
    void shouldGenerateQrCodeDataUrl() {
        String secret = totpService.generateSecret();
        String otpauthUrl = totpService.getOtpauthUrl("admin@wbagency.com.br", secret);
        String qrCodeDataUrl = totpService.generateQrCodeDataUrl(otpauthUrl);

        assertNotNull(qrCodeDataUrl);
        assertTrue(qrCodeDataUrl.startsWith("data:image/png;base64,"));
        assertTrue(qrCodeDataUrl.length() > 100);
    }

    @Test
    @DisplayName("Deve validar código de backup gerado e seu respectivo hash")
    void shouldGenerateAndVerifyBackupCodes() {
        List<String> codes = totpService.generateBackupCodes(8);
        assertEquals(8, codes.size());

        for (String code : codes) {
            assertTrue(code.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}$"));
            String hash = totpService.hashBackupCode(code);
            assertNotNull(hash);
            assertTrue(totpService.verifyBackupCode(code, hash));
            assertFalse(totpService.verifyBackupCode("INVALIDO", hash));
        }
    }

    @Test
    @DisplayName("RFC 6238: Deve validar código atual ou rejeitar código inválido/fora da janela")
    void shouldVerifyTotpCodeWithinStrictWindow() throws Exception {
        String secret = totpService.generateSecret();

        // Código nulo ou em branco
        assertFalse(totpService.verifyCode(secret, null));
        assertFalse(totpService.verifyCode(secret, ""));
        assertFalse(totpService.verifyCode(secret, "12345")); // menos de 6 dígitos
        assertFalse(totpService.verifyCode(secret, "1234567")); // mais de 6 dígitos
        assertFalse(totpService.verifyCode(secret, "ABCDEF")); // não numérico

        // Gerar código válido usando método interno via reflexão
        java.lang.reflect.Method decodeBase32 = TotpService.class.getDeclaredMethod("decodeBase32", String.class);
        decodeBase32.setAccessible(true);
        byte[] keyBytes = (byte[]) decodeBase32.invoke(totpService, secret);

        java.lang.reflect.Method generateCode = TotpService.class.getDeclaredMethod("generateCodeForStep", byte[].class, long.class);
        generateCode.setAccessible(true);

        long currentStep = java.time.Instant.now().getEpochSecond() / 30;

        // Código do passo atual
        String currentCode = (String) generateCode.invoke(totpService, keyBytes, currentStep);
        assertTrue(totpService.verifyCode(secret, currentCode));

        // Código do passo anterior (-1 passo = -30 segundos)
        String prevCode = (String) generateCode.invoke(totpService, keyBytes, currentStep - 1);
        assertTrue(totpService.verifyCode(secret, prevCode));

        // Código do passo seguinte (+1 passo = +30 segundos)
        String nextCode = (String) generateCode.invoke(totpService, keyBytes, currentStep + 1);
        assertTrue(totpService.verifyCode(secret, nextCode));

        // Código fora da janela estrita (-2 passos = -60 segundos)
        String tooOldCode = (String) generateCode.invoke(totpService, keyBytes, currentStep - 2);
        assertFalse(totpService.verifyCode(secret, tooOldCode));

        // Código fora da janela estrita (+2 passos = +60 segundos)
        String tooFarAheadCode = (String) generateCode.invoke(totpService, keyBytes, currentStep + 2);
        assertFalse(totpService.verifyCode(secret, tooFarAheadCode));
    }
}
