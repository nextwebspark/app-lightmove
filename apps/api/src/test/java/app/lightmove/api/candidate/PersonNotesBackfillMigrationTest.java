package app.lightmove.api.candidate;

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
 * V96: every mandate's note becomes a general note on the person, about that mandate, by whoever filed
 * the row, as of its last edit — with the timeline line saying so — and a blank one becomes nothing.
 *
 * <p>Its own container and Flyway run, stopped at V95 to seed and carried on to V96, for
 * {@link CandidatePersonBackfillMigrationTest}'s reason.
 */
class PersonNotesBackfillMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CFO = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID CREDIT = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID QUIET = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

    @Test
    @DisplayName("V96 files each mandate's note as a note on the person, about that mandate")
    void filesMandateNotesOnThePerson() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "95");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "96");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT kind || '|' || body || '|' || project_title || '|' || author_user_id || '|'
                               || to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
                        FROM app_lm_person_note WHERE person_id = ? ORDER BY created_at""", PERSON))
                        .containsExactly(
                                "GENERAL|Prefers Riyadh.|Chief Financial Officer|" + USER + "|2021-03-01",
                                "GENERAL|Declined: competing client.|Head of Credit Risk|" + USER + "|2021-04-01");
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_person_note WHERE person_id = ?""", QUIET))
                        .containsExactly("0");
                assertThat(strings(connection, """
                        SELECT a.kind || '|' || a.project_title || '|' || (a.details ->> 'noteId' = n.id::text)
                        FROM app_lm_person_activity a JOIN app_lm_person_note n ON n.id::text = a.details ->> 'noteId'
                        WHERE a.person_id = ? ORDER BY a.occurred_at""", PERSON))
                        .containsExactly("NOTE_ADDED|Chief Financial Officer|true",
                                "NOTE_ADDED|Head of Credit Risk|true");
                // The mandate's own copy is left frozen for the cleanup migration (#606).
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_project_candidate WHERE note IS NOT NULL""", null))
                        .containsExactly("3");
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
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s'),
                           ('%s', '%s', '%s', 'Head of Credit Risk', '%s')"""
                    .formatted(CFO, WORKSPACE, CLIENT, USER, CREDIT, WORKSPACE, CLIENT, USER));
            statement.execute("""
                    INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                    VALUES ('%s', '%s', 'Fatima Al Mazrouei', 'MANUAL', '%s'),
                           ('%s', '%s', 'Quiet Person', 'MANUAL', '%s')"""
                    .formatted(PERSON, WORKSPACE, USER, QUIET, WORKSPACE, USER));
        }
        mapping(connection, CFO, PERSON, "  Prefers Riyadh.  ", "2021-03-01");
        mapping(connection, CREDIT, PERSON, "Declined: competing client.", "2021-04-01");
        mapping(connection, CFO, QUIET, "   ", "2021-05-01");
    }

    private static void mapping(Connection connection, UUID projectId, UUID personId, String note, String updatedOn)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_candidate (project_id, person_id, note, added_by, updated_at)
                VALUES (?, ?, ?, ?, ?::date)""")) {
            statement.setObject(1, projectId);
            statement.setObject(2, personId);
            statement.setString(3, note);
            statement.setObject(4, USER);
            statement.setString(5, updatedOn);
            statement.execute();
        }
    }

    private static List<String> strings(Connection connection, String sql, Object parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setObject(1, parameter);
            }
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
