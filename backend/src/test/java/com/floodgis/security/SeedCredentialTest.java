package com.floodgis.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedCredentialTest {
    @Test
    void documentedDemoPasswordMatchesMigrationHash() throws Exception {
        String sql;
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V1__init.sql")) {
            if (input == null) throw new IllegalStateException("migration not found");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String marker = "('admin',    '";
        int start = sql.indexOf(marker) + marker.length();
        String hash = sql.substring(start, sql.indexOf("'", start));
        assertTrue(new BCryptPasswordEncoder().matches("admin123", hash));
    }
}
