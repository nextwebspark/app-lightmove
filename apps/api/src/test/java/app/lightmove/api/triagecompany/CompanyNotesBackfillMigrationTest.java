package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V143: each company's single note becomes its first note — general, plain, by whoever added the company, as of
 * the row's last change — and a blank one becomes nothing. Its own container, stopped at V142 to seed, for
 * {@code PersonNotesBackfillMigrationTest}'s reason.
 */
class CompanyNotesBackfillMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID NOTED = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID BLANK = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

    @Test
    @DisplayName("V143 files each company's note as its first note, and a blank one as nothing")
    void filesEachCompanysNote() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "142");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "143");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT kind || '|' || body || '|' || body_format || '|' || author_user_id || '|'
                               || workspace_id || '|' || to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
                        FROM app_lm_company_note WHERE triage_company_id = ?""", NOTED))
                        .containsExactly("GENERAL|Met their CFO at ADIPEC.|PLAIN|" + USER + "|" + WORKSPACE
                                + "|2021-03-01");
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_company_note WHERE triage_company_id = ?""", BLANK))
                        .containsExactly("0");
                // The row's own copy is left frozen rather than dropped.
                assertThat(strings(connection, """
                        SELECT note FROM app_lm_project_triage_company WHERE id = ?""", NOTED))
                        .containsExactly("  Met their CFO at ADIPEC.  ");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'seed@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Seed Firm', 'seed-firm', 'example.com', '%s')""".formatted(WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(PROJECT, WORKSPACE, CLIENT, USER));
        }
        company(connection, NOTED, "Gulf Industrial", "  Met their CFO at ADIPEC.  ", "2021-03-01");
        company(connection, BLANK, "Harbour Ports", "   ", "2021-04-01");
    }

    private static void company(Connection connection, UUID id, String name, String note, String updatedOn)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_triage_company
                    (id, project_id, source, status, note, company_name, added_by, updated_at)
                VALUES (?, ?, 'MANUAL', 'IN_UNIVERSE', ?, ?, ?, ?::timestamp AT TIME ZONE 'UTC')""")) {
            statement.setObject(1, id);
            statement.setObject(2, PROJECT);
            statement.setString(3, note);
            statement.setString(4, name);
            statement.setObject(5, USER);
            statement.setString(6, updatedOn);
            statement.execute();
        }
    }

    private static List<String> strings(Connection connection, String sql, Object parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
                return values;
            }
        }
    }

    private static void migrateTo(PostgreSQLContainer postgres, String target) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .placeholders(Map.of("iam_user", ""))
                .target(target)
                .load()
                .migrate();
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
