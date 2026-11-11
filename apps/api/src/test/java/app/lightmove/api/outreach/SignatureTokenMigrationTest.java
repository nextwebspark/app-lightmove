package app.lightmove.api.outreach;

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
 * V207 and V208: the {@code {{signature}}} token goes from every stored step and frozen first email, subjects included,
 * its gap closed, while a run's chosen signature stays. Its own container, stopped at V206 to seed.
 */
class SignatureTokenMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000207a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000207b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000207c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000207d1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000207e1");
    private static final UUID SEQUENCE = UUID.fromString("00000000-0000-0000-0000-0000000207f1");
    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000020701");

    @Test
    @DisplayName("V207 and V208 take {{signature}} out of every step and frozen first email, and keep the run's signature")
    void takesTheTokenOutOfTheWords() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "206");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "208");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT body FROM app_lm_outreach_sequence_step WHERE sequence_id = ? ORDER BY sort_order""",
                        SEQUENCE)).containsExactly("Hi {{firstName}},\n\nWould a call work?", "Hi,\n\nP.S. Soon",
                        "No token here.");
                assertThat(strings(connection, """
                        SELECT coalesce(subject, '-') FROM app_lm_outreach_sequence_step WHERE sequence_id = ?
                        ORDER BY sort_order""", SEQUENCE)).containsExactly("Confidential note", "-", "-");
                assertThat(strings(connection, """
                        SELECT first_subject FROM app_lm_outreach_enrollment WHERE id = ?""", RUN))
                        .containsExactly("Confidential note");
                assertThat(strings(connection, """
                        SELECT first_body || '|' || signature_html FROM app_lm_outreach_enrollment WHERE id = ?""",
                        RUN)).containsExactly("Hi Priya,\n\nWould a call work?|<table>Yara</table>");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'yara@firm.example', 'Yara Haddad')"""
                .formatted(USER));
        execute(connection, """
                INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                VALUES ('%s', 'Seed Firm', 'seed-firm', 'firm.example', '%s')""".formatted(WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')""".formatted(PROJECT, WORKSPACE, CLIENT, USER));
        execute(connection, """
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES ('%s', '%s', 'Priya Raman', 'MANUAL', '%s')""".formatted(PERSON, WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_outreach_sequence (id, workspace_id, project_id, name)
                VALUES ('%s', '%s', '%s', 'First approach')""".formatted(SEQUENCE, WORKSPACE, PROJECT));
        execute(connection, """
                INSERT INTO app_lm_outreach_sequence_step (sequence_id, sort_order, delay_working_days, subject, body)
                VALUES ('%1$s', 0, 0, 'Confidential {{signature}} note', E'Hi {{firstName}},\\n\\nWould a call work?\\n\\n{{signature}}'),
                       ('%1$s', 1, 3, NULL, E'Hi,\\n\\n{{ signature|Yara }}\\n\\nP.S. Soon'),
                       ('%1$s', 2, 2, NULL, 'No token here.')""".formatted(SEQUENCE));
        execute(connection, """
                INSERT INTO app_lm_outreach_enrollment (id, workspace_id, project_id, sequence_id, person_id,
                                                        sender_user_id, to_address, status, first_subject,
                                                        first_body, enrolled_at, signature_html)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'priya@target.example', 'SCHEDULED', 'Confidential {{ signature }} note',
                        E'Hi Priya,\\n\\nWould a call work?\\n\\n{{signature}}\\n', now(), '<table>Yara</table>')"""
                .formatted(RUN, WORKSPACE, PROJECT, SEQUENCE, PERSON, USER));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
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
