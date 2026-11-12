package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V211: a zone stays pinned only where its person chose one, as the audit trail records; everyone else becomes
 * Automatic, their device's zone seeded with the one they read in, so nobody's clock moves on deploy. Its own
 * container, stopped at V210 to seed.
 */
class AutomaticTimeZoneMigrationTest {

    private static final UUID DUBAI_FIRM = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHOOSER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNDONE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID NEVER_CHOSE = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID FOLLOWER = UUID.fromString("00000000-0000-0000-0000-0000000000a4");

    @Test
    @DisplayName("V211 keeps a chosen zone pinned, makes everyone else Automatic, and moves nobody's clock")
    void pinsOnlyWhatWasChosen() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "209");
            try (Connection connection = connect(postgres)) {
                user(connection, CHOOSER, "Asia/Kolkata");
                user(connection, UNDONE, "Asia/Dubai");
                user(connection, NEVER_CHOSE, "Europe/London");
                user(connection, FOLLOWER, "Asia/Dubai");
                workspace(connection, DUBAI_FIRM, "dubai-firm");
                for (UUID person : new UUID[] {CHOOSER, UNDONE, NEVER_CHOSE, FOLLOWER}) {
                    member(connection, DUBAI_FIRM, person);
                }
            }
            migrateTo(postgres, "210");
            try (Connection connection = connect(postgres)) {
                profileUpdated(connection, CHOOSER, "Asia/Kolkata", "2026-10-01T09:00:00Z");
                profileUpdated(connection, UNDONE, "Asia/Tokyo", "2026-10-01T09:00:00Z");
                profileUpdated(connection, UNDONE, "workspace", "2026-10-02T09:00:00Z");
            }

            migrateTo(postgres, "211");

            try (Connection connection = connect(postgres)) {
                assertThat(column(connection, "timezone", CHOOSER)).isEqualTo("Asia/Kolkata");
                assertThat(column(connection, "device_time_zone", CHOOSER)).isEqualTo("Asia/Kolkata");
                assertThat(column(connection, "timezone", UNDONE)).isNull();
                assertThat(column(connection, "device_time_zone", UNDONE)).isEqualTo("Asia/Dubai");
                // V210 kept their London as if chosen; nothing says they chose it, so it is their device's now.
                assertThat(column(connection, "timezone", NEVER_CHOSE)).isNull();
                assertThat(column(connection, "device_time_zone", NEVER_CHOSE)).isEqualTo("Europe/London");
                assertThat(column(connection, "timezone", FOLLOWER)).isNull();
                assertThat(column(connection, "device_time_zone", FOLLOWER)).isEqualTo("Asia/Dubai");
            }
        }
    }

    private static void user(Connection connection, UUID id, String zone) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_user (id, email, full_name, timezone) VALUES (?, ?, 'Seed User', ?)""")) {
            statement.setObject(1, id);
            statement.setString(2, id + "@example.com");
            statement.setString(3, zone);
            statement.execute();
        }
    }

    private static void workspace(Connection connection, UUID id, String slug) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by, mode)
                VALUES (?, ?, ?, 'example.com', ?, 'COMPANY')""")) {
            statement.setObject(1, id);
            statement.setString(2, slug);
            statement.setString(3, slug);
            statement.setObject(4, CHOOSER);
            statement.execute();
        }
    }

    private static void member(Connection connection, UUID workspaceId, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_workspace_member (id, workspace_id, user_id, status)
                VALUES (gen_random_uuid(), ?, ?, 'ACTIVE')""")) {
            statement.setObject(1, workspaceId);
            statement.setObject(2, userId);
            statement.execute();
        }
    }

    private static void profileUpdated(Connection connection, UUID actor, String zoneTo, String at)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_audit_event (occurred_at, event_type, outcome, actor_user_id, workspace_id, metadata)
                VALUES (?::timestamptz, 'PROFILE_UPDATED', 'SUCCESS', ?, ?, jsonb_build_object('timezoneTo', ?))""")) {
            statement.setString(1, at);
            statement.setObject(2, actor);
            statement.setObject(3, DUBAI_FIRM);
            statement.setString(4, zoneTo);
            statement.execute();
        }
    }

    private static String column(Connection connection, String name, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + name + " FROM app_lm_user WHERE id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
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
