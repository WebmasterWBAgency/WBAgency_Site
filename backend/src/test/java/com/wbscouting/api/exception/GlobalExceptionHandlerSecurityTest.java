package com.wbscouting.api.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerSecurityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DataAccessException: Deve retornar 500 com mensagem genérica sem vazar SQL ou tabelas")
    void handleDataAccessException_Sanitized() {
        DataAccessException ex = new DataAccessException("SELECT * FROM secret_admins WHERE password_hash = '123' failed due to syntax error") {};

        ProblemDetail pd = handler.handleDataAccessException(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("Ocorreu um erro no processamento de dados do servidor. A equipe técnica foi notificada.");
        assertThat(pd.getDetail()).doesNotContain("secret_admins");
        assertThat(pd.getDetail()).doesNotContain("password_hash");
        assertThat(pd.getProperties()).doesNotContainKey("debug_message");
    }

    @Test
    @DisplayName("TransactionSystemException: Deve retornar 500 genérico sem vazar causa raiz interna")
    void handleTransactionSystemException_Sanitized() {
        TransactionSystemException ex = new TransactionSystemException("Could not commit JPA transaction", new RuntimeException("FK constraint failure on candidate_submissions_id"));

        ProblemDetail pd = handler.handleTransactionSystemException(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("Não foi possível concluir a transação de dados no servidor.");
        assertThat(pd.getDetail()).doesNotContain("candidate_submissions_id");
        assertThat(pd.getProperties()).doesNotContainKey("debug_message");
    }

    @Test
    @DisplayName("Generic Exception: Deve retornar 500 genérico sem expor stack trace no debug_message")
    void handleGenericException_NoDebugMessage() {
        RuntimeException ex = new RuntimeException("NullPointerException at com.wbscouting.api.SecretClass.execute(SecretClass.java:42)");

        ProblemDetail pd = handler.handleGenericException(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("Ocorreu um erro interno inesperado no servidor. A equipe técnica foi notificada.");
        assertThat(pd.getProperties()).doesNotContainKey("debug_message");
    }
}
