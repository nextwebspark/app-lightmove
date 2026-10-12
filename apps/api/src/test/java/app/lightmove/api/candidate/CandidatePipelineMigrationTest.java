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
 * V130: every workspace gets the seeded pipeline, and every executive keeps the meaning their old enum
 * value had — the system status of that name — dated by its last status line on the position, else by when it
 * was mapped (never by updated_at, which any edit moves). A row written by a revision
 * that predates the catalog still lands on the right status.
 *
 * <p>Its own container and Flyway run, stopped at V129 to seed and carried on to V130, for
 * {@link CandidatePersonBackfillMigrationTest}'s reason.
 */
class CandidatePipelineMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000001a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000001b1");
    private static final UUID EMPTY_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000001b2");
    private static final UUID LATER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000001b3");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000001c1");
    private static final UUID LATER_CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000001c3");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000001d1");
    private static final UUID LATER_PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000001d3");

    private static final List<String> OLD_STATUSES = List.of("IDENTIFIED", "CONTACTED", "ENGAGED", "INTERESTED",
            "NOT_INTERESTED", "OFF_LIMITS", "OUT_OF_SCOPE");

    @Test
    @DisplayName("V130 seeds every workspace and keeps each executive's status, by its system key")
    void everyRowKeepsItsMeaning() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "129");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "130");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT c.status || '|' || s.key || '|' || coalesce(s.system_key, '') || '|' || g.key
                               || '|' || to_char(c.status_changed_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
                        FROM app_lm_project_candidate c
                        JOIN app_lm_workspace_candidate_status s ON s.id = c.status_id
                        JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id
                        WHERE s.workspace_id = ? ORDER BY c.updated_at""", WORKSPACE))
                        .containsExactly(
                                "IDENTIFIED|identified|IDENTIFIED|identified|2024-06-01",
                                "CONTACTED|contacted|CONTACTED|contacted|2025-03-01",
                                "ENGAGED|engaged|ENGAGED|contacted|2024-06-03",
                                "INTERESTED|interested|INTERESTED|interested|2024-06-04",
                                "NOT_INTERESTED|notInterested|NOT_INTERESTED|declined|2024-06-05",
                                "OFF_LIMITS|offLimits|OFF_LIMITS|declined|2024-06-06",
                                "OUT_OF_SCOPE|outOfScope|OUT_OF_SCOPE|declined|2024-06-07");
                for (UUID workspace : List.of(WORKSPACE, EMPTY_WORKSPACE)) {
                    assertThat(strings(connection, """
                            SELECT g.key || '|' || g.kind || '|' || count(s.id) || '|'
                                   || coalesce(max(CASE WHEN s.is_default THEN s.key END), '')
                            FROM app_lm_workspace_candidate_stage g
                            JOIN app_lm_workspace_candidate_status s ON s.stage_id = g.id
                            WHERE g.workspace_id = ? GROUP BY g.key, g.kind, g.position ORDER BY g.position""",
                            workspace))
                            .containsExactly("identified|ACTIVE|3|identified", "contacted|ACTIVE|4|contacted",
                                    "interested|ACTIVE|4|interested", "qualified|ACTIVE|3|screened",
                                    "shortlisted|ACTIVE|4|shortlisted", "declined|CLOSED|7|notInterested");
                }

                seedLaterWorkspace(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO app_lm_project_candidate (project_id, person_id, source, added_by, status)
                            SELECT '%s', p.id, 'MANUAL', '%s', 'OFF_LIMITS' FROM app_lm_person p
                            WHERE p.workspace_id = '%s'""".formatted(LATER_PROJECT, USER, LATER_WORKSPACE));
                }
                assertThat(strings(connection, """
                        SELECT s.key FROM app_lm_project_candidate c
                        JOIN app_lm_workspace_candidate_status s ON s.id = c.status_id
                        WHERE c.project_id = ?""", LATER_PROJECT))
                        .as("a row written without a status id, in a workspace nothing had seeded yet")
                        .containsExactly("offLimits");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'pipeline@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Seed Firm', 'seed-firm', 'example.com', '%s'),
                           ('%s', 'Empty Firm', 'empty-firm', 'example.com', '%s')"""
                    .formatted(WORKSPACE, USER, EMPTY_WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(PROJECT, WORKSPACE, CLIENT, USER));
        }
        for (int index = 0; index < OLD_STATUSES.size(); index++) {
            UUID person = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                    VALUES (?, ?, ?, 'MANUAL', ?)""")) {
                statement.setObject(1, person);
                statement.setObject(2, WORKSPACE);
                statement.setString(3, "Person " + index);
                statement.setObject(4, USER);
                statement.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app_lm_project_candidate (project_id, person_id, status, added_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, timestamptz '2024-06-01 12:00:00+00' + make_interval(days => ?),
                            timestamptz '2025-01-01 00:00:00+00' + make_interval(days => ?))""")) {
                statement.setObject(1, PROJECT);
                statement.setObject(2, person);
                statement.setString(3, OLD_STATUSES.get(index));
                statement.setObject(4, USER);
                statement.setInt(5, index);
                statement.setInt(6, index);
                statement.execute();
            }
            if ("CONTACTED".equals(OLD_STATUSES.get(index))) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, kind, occurred_at)
                        VALUES (?, ?, ?, 'STATUS_CHANGED', timestamptz '2025-02-01 12:00:00+00'),
                               (?, ?, ?, 'STATUS_CHANGED', timestamptz '2025-03-01 12:00:00+00'),
                               (?, ?, ?, 'PROFILE_EDITED', timestamptz '2025-04-01 12:00:00+00')""")) {
                    for (int row = 0; row < 3; row++) {
                        statement.setObject(row * 3 + 1, WORKSPACE);
                        statement.setObject(row * 3 + 2, person);
                        statement.setObject(row * 3 + 3, PROJECT);
                    }
                    statement.execute();
                }
            }
        }
    }

    private static void seedLaterWorkspace(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Later Firm', 'later-firm', 'example.com', '%s')""".formatted(LATER_WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Later Unit', '%s')""".formatted(LATER_CLIENT, LATER_WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief People Officer', '%s')"""
                    .formatted(LATER_PROJECT, LATER_WORKSPACE, LATER_CLIENT, USER));
            statement.execute("""
                    INSERT INTO app_lm_person (workspace_id, full_name, source, created_by)
                    VALUES ('%s', 'Later Person', 'MANUAL', '%s')""".formatted(LATER_WORKSPACE, USER));
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
