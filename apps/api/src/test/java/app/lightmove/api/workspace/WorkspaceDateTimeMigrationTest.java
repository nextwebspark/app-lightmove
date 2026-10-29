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
 * V160: nobody's clock moves on deploy. A workspace takes the zone most of its members keep, and a person follows it
 * only where that changes nothing in any workspace they are in. Its own container, stopped at V159 to seed.
 */
class WorkspaceDateTimeMigrationTest {

    private static final UUID DUBAI_FIRM = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID LONDON_FIRM = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID EMPTY_FIRM = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID IN_BOTH = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DUBAI_ONLY = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID LONDONER_IN_DUBAI = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID LONDON_ONE = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final UUID LONDON_TWO = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
    private static final UUID IN_NONE = UUID.fromString("00000000-0000-0000-0000-0000000000a6");

    @Test
    @DisplayName("V160 gives each workspace its members' zone and leaves a person their own only where it differs")
    void keepsEveryClockWhereItWas() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "159");
            try (Connection connection = connect(postgres)) {
                user(connection, IN_BOTH, "Asia/Dubai");
                user(connection, DUBAI_ONLY, "Asia/Dubai");
                user(connection, LONDONER_IN_DUBAI, "Europe/London");
                user(connection, LONDON_ONE, "Europe/London");
                user(connection, LONDON_TWO, "Europe/London");
                user(connection, IN_NONE, "Asia/Riyadh");
                workspace(connection, DUBAI_FIRM, "dubai-firm");
                workspace(connection, LONDON_FIRM, "london-firm");
                workspace(connection, EMPTY_FIRM, "empty-firm");
                member(connection, DUBAI_FIRM, IN_BOTH);
                member(connection, DUBAI_FIRM, DUBAI_ONLY);
                member(connection, DUBAI_FIRM, LONDONER_IN_DUBAI);
                member(connection, LONDON_FIRM, IN_BOTH);
                member(connection, LONDON_FIRM, LONDON_ONE);
                member(connection, LONDON_FIRM, LONDON_TWO);
            }

            migrateTo(postgres, "160");

            try (Connection connection = connect(postgres)) {
                assertThat(value(connection, "SELECT time_zone FROM app_lm_workspace WHERE id = ?", DUBAI_FIRM))
                        .isEqualTo("Asia/Dubai");
                assertThat(value(connection, "SELECT time_zone FROM app_lm_workspace WHERE id = ?", LONDON_FIRM))
                        .isEqualTo("Europe/London");
                assertThat(value(connection, "SELECT time_zone FROM app_lm_workspace WHERE id = ?", EMPTY_FIRM))
                        .isNull();
                assertThat(value(connection, "SELECT date_format FROM app_lm_workspace WHERE id = ?", DUBAI_FIRM))
                        .isNull();

                assertThat(zoneOf(connection, DUBAI_ONLY)).isNull();
                assertThat(zoneOf(connection, LONDON_ONE)).isNull();
                assertThat(zoneOf(connection, LONDONER_IN_DUBAI)).isEqualTo("Europe/London");
                // Dubai's workspace keeps Dubai, but London's would move them: theirs stays their own.
                assertThat(zoneOf(connection, IN_BOTH)).isEqualTo("Asia/Dubai");
                assertThat(zoneOf(connection, IN_NONE)).isEqualTo("Asia/Riyadh");
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
            statement.setObject(4, IN_BOTH);
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

    private static String zoneOf(Connection connection, UUID userId) throws SQLException {
        return value(connection, "SELECT timezone FROM app_lm_user WHERE id = ?", userId);
    }

    private static String value(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
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
