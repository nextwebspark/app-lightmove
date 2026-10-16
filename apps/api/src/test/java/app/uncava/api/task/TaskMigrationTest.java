package app.uncava.api.task;

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
 * V133: every V132 next action becomes an open task — held by whoever set it, else the position's first lead —
 * its notices counted as sent, and every workspace gets the eight presets. Its own container and Flyway run,
 * stopped at V132 to seed and carried on to V133, as {@code CandidatePipelineMigrationTest} does.
 */
class TaskMigrationTest {

    private static final UUID SETTER = UUID.fromString("00000000-0000-0000-0000-0000000003a1");
    private static final UUID LEAD = UUID.fromString("00000000-0000-0000-0000-0000000003a2");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000003b1");
    private static final UUID LEAD_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000003b2");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000003c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000003d1");
    private static final UUID SEAT = UUID.fromString("00000000-0000-0000-0000-0000000003e1");

    @Test
    @DisplayName("V133 turns each next action into an open task for its setter, else the lead, and seeds presets")
    void nextActionsBecomeTasks() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "132");
            try (Connection connection = connect(postgres)) {
                seed(connection);
                candidate(connection, "Layla Haddad", "Call after the board meeting", SETTER);
                candidate(connection, "Omar Said", "Send the shortlist note", null);
                candidate(connection, "Hana Aziz", null, null);
            }

            migrateTo(postgres, "133");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT t.title || '|' || t.assignee_user_id || '|' || coalesce(t.created_by::text, '-') || '|'
                               || t.status || '|' || t.source || '|' || (t.due_soon_notified_at IS NOT NULL)
                               || '|' || (c.id = t.candidate_id)
                        FROM app_lm_task t JOIN app_lm_project_candidate c ON c.person_id = t.person_id
                        WHERE t.workspace_id = ? ORDER BY t.title""", WORKSPACE))
                        .containsExactly(
                                "Call after the board meeting|" + SETTER + "|" + SETTER + "|OPEN|MANUAL|true|true",
                                "Send the shortlist note|" + LEAD + "|-|OPEN|MANUAL|true|true");
                assertThat(strings(connection, """
                        SELECT label FROM app_lm_workspace_task_preset WHERE workspace_id = ? ORDER BY position""",
                        WORKSPACE))
                        .containsExactly("Call to qualify interest", "Send follow-up email", "Schedule a call",
                                "Reference check", "Share profile with hiring manager", "Request CV",
                                "Debrief after meeting", "Re-engage later");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name)
                    VALUES ('%s', 'setter@example.com', 'Set By'), ('%s', 'lead@example.com', 'The Lead')"""
                    .formatted(SETTER, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Task Firm', 'task-firm', 'example.com', '%s')""".formatted(WORKSPACE, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_workspace_member (id, workspace_id, user_id, status)
                    VALUES ('%s', '%s', '%s', 'ACTIVE')""".formatted(LEAD_MEMBER, WORKSPACE, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(PROJECT, WORKSPACE, CLIENT, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_project_member (id, project_id, member_id, added_by)
                    VALUES ('%s', '%s', '%s', '%s')""".formatted(SEAT, PROJECT, LEAD_MEMBER, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_project_member_role (project_member_id, role_id)
                    SELECT '%s', id FROM app_lm_role WHERE name = 'LEAD' AND scope = 'PROJECT'""".formatted(SEAT));
        }
    }

    private static void candidate(Connection connection, String fullName, String nextAction, UUID setBy)
            throws SQLException {
        UUID person = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES (?, ?, ?, 'MANUAL', ?)""")) {
            statement.setObject(1, person);
            statement.setObject(2, WORKSPACE);
            statement.setString(3, fullName);
            statement.setObject(4, LEAD);
            statement.execute();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_candidate (project_id, person_id, status, added_by, next_action,
                                                      next_action_due, next_action_set_by)
                VALUES (?, ?, 'CONTACTED', ?, ?, CASE WHEN CAST(? AS text) IS NULL THEN NULL
                                                      ELSE timestamptz '2026-11-02 09:00:00+00' END, ?)""")) {
            statement.setObject(1, PROJECT);
            statement.setObject(2, person);
            statement.setObject(3, LEAD);
            statement.setString(4, nextAction);
            statement.setString(5, nextAction);
            statement.setObject(6, setBy);
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
