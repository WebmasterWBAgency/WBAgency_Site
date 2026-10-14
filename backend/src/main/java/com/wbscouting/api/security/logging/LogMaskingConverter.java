package com.wbscouting.api.security.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversor de Logback para higienização e mascaramento de PII (Personally Identifiable Information)
 * e credenciais sensíveis (password, cpf, phone, email, totpSecret, tokens).
 */
public class LogMaskingConverter extends MessageConverter {

    private static final String MASK = "***MASKED***";

    // 1. JSON key-value: "key": "value" ou "key": value
    private static final Pattern JSON_SENSITIVE_PATTERN = Pattern.compile(
            "\"(password|passwordHash|totpSecret|secret|token|tempToken|tempChallengeToken|cpf|phone|email|guardianPhone|guardianEmail)\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );

    // 2. URI query params / Key-Value: key=value
    private static final Pattern KV_SENSITIVE_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwordHash|totpSecret|secret|token|tempToken|tempChallengeToken|cpf|phone|email)\\s*=\\s*([^\\s&,;\"'\\)\\]]+)"
    );

    // 3. CPF padrão (11 dígitos, formatado ou contínuo)
    private static final Pattern CPF_PATTERN = Pattern.compile(
            "\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b"
    );

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        return mask(message);
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Mascarar JSON
        Matcher jsonMatcher = JSON_SENSITIVE_PATTERN.matcher(message);
        if (jsonMatcher.find()) {
            message = jsonMatcher.replaceAll("\"$1\": \"" + MASK + "\"");
        }

        // Mascarar Key-Value
        Matcher kvMatcher = KV_SENSITIVE_PATTERN.matcher(message);
        if (kvMatcher.find()) {
            message = kvMatcher.replaceAll("$1=" + MASK);
        }

        // Mascarar CPF formatado
        Matcher cpfMatcher = CPF_PATTERN.matcher(message);
        if (cpfMatcher.find()) {
            message = cpfMatcher.replaceAll(MASK);
        }

        return message;
    }
}
