package com.findguni.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "spring.datasource.url=jdbc:h2:mem:findguni-migration-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "debug=false"
})
class MysqlMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void baselineAndQrExplorationMigrationsCreateTheRequiredColumns() {
        assertThat(columnExists("ESCAPE_GAMES", "FLOW_MODE")).isTrue();
        assertThat(columnExists("GAME_STAGES", "QR_ENABLED")).isTrue();
        assertThat(columnExists("GAME_STAGES", "ENTRY_MODE")).isTrue();
        assertThat(columnExists("GAME_STAGES", "NEXT_STAGE_KEY")).isTrue();
        assertThat(columnExists("PLAY_SESSIONS", "DISCOVERED_STAGES_JSON")).isTrue();
        assertThat(columnExists("PLAY_SESSIONS", "SOLVED_STAGES_JSON")).isTrue();
        assertThat(columnExists("PLAY_SESSIONS", "ACTIVE_STAGE_KEY")).isTrue();
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
