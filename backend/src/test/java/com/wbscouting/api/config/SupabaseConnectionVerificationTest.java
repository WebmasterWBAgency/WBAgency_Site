package com.wbscouting.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class SupabaseConnectionVerificationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConnectAndQueryDatabaseSuccessfully() throws Exception {
        assertNotNull(dataSource, "DataSource não pode ser nulo");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next(), "A consulta de verificação de conexão deve retornar resultado");
        }
    }
}
