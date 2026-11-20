package app.lightmove.api.task;

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

/** V230: an open task an executive's removal left behind is cancelled; one a rule filed for nobody mapped is not. */
class OrphanedTaskMigrationTest {

    private static final UUID LEAD = UUID.fromString("00000000-0000-0000-0000-0000000004a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000004b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000004c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000004d1");

    @Test
    @DisplayName("V230 cancels the open tasks an unmapping orphaned, releasing a rule's key, and nothing else")
    void orphanedOpenTasksAreCancelled() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "229");
            try (Connection connection = connect(postgres)) {
                seed(connection);
                UUID removed = person(connection, "Layla Haddad");
                task(connection, removed, "Orphaned call", "OPEN", null, "2026-11-01 09:00");
                task(connection, removed, "Orphaned reply", "OPEN", "reply:m1", "2026-11-01 09:00");
                task(connection, removed, "Done before removal", "DONE", null, "2026-11-01 09:00");
                unmapped(connection, removed, "2026-11-02 09:00");
                UUID neverMapped = person(connection, "Omar Said");
                unmapped(connection, neverMapped, "2026-10-01 09:00");
                task(connection, neverMapped, "Reply filed after", "OPEN", "reply:m2", "2026-11-01 09:00");
            }

            migrateTo(postgres, "230");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT title || '|' || status || '|' || coalesce(rule_key, '-') FROM app_lm_task
                        WHERE workspace_id = ? ORDER BY title""", WORKSPACE))
                        .containsExactly(
                                "Done before removal|DONE|-",
                                "Orphaned call|CANCELLED|-",
                                "Orphaned reply|CANCELLED|reply:m1:released:" + taskId(connection, "Orphaned reply"),
                                "Reply filed after|OPEN|reply:m2");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'lead@example.com', 'The Lead')"""
                    .formatted(LEAD));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Orphan Firm', 'orphan-firm', 'example.com', '%s')""".formatted(WORKSPACE, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, LEAD));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(PROJECT, WORKSPACE, CLIENT, LEAD));
        }
    }

    private static UUID person(Connection connection, String fullName) throws SQLException {
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
        return person;
    }

    private static void task(Connection connection, UUID person, String title, String status, String ruleKey,
                             String createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_task (workspace_id, project_id, person_id, title, kind, assignee_user_id, status,
                                         source, rule, rule_key, created_by, completed_at, created_at)
                VALUES (?, ?, ?, ?, 'TODO', ?, ?, CASE WHEN CAST(? AS text) IS NULL THEN 'MANUAL' ELSE 'RULE' END,
                        CASE WHEN CAST(? AS text) IS NULL THEN NULL ELSE 'REPLY' END, ?,
                        CASE WHEN CAST(? AS text) IS NULL THEN ?::uuid END,
                        CASE WHEN ? = 'DONE' THEN timestamptz '2026-11-01 12:00+00' END,
                        CAST(? AS timestamptz))""")) {
            statement.setObject(1, WORKSPACE);
            statement.setObject(2, PROJECT);
            statement.setObject(3, person);
            statement.setString(4, title);
            statement.setObject(5, LEAD);
            statement.setString(6, status);
            statement.setString(7, ruleKey);
            statement.setString(8, ruleKey);
            statement.setString(9, ruleKey);
            statement.setString(10, ruleKey);
            statement.setString(11, LEAD.toString());
            statement.setString(12, status);
            statement.setString(13, createdAt + "+00");
            statement.execute();
        }
    }

    private static void unmapped(Connection connection, UUID person, String occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, actor_user_id, kind,
                                                    occurred_at)
                VALUES (?, ?, ?, ?, 'UNMAPPED', CAST(? AS timestamptz))""")) {
            statement.setObject(1, WORKSPACE);
            statement.setObject(2, person);
            statement.setObject(3, PROJECT);
            statement.setObject(4, LEAD);
            statement.setString(5, occurredAt + "+00");
            statement.execute();
        }
    }

    private static String taskId(Connection connection, String title) throws SQLException {
        return strings(connection, "SELECT id::text FROM app_lm_task WHERE title = ?", title).getFirst();
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
