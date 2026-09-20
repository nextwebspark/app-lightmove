package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V66's location backfill, run against rows written the way the application wrote them before it: a
 * "city, country" line, two cities before a country, a bare country seeded from the client's HQ,
 * free text, and nothing at all.
 *
 * <p>The ordinary integration suite migrates an empty schema, so this is the one place the UPDATE
 * statements meet data. It takes its own container and its own Flyway run — stopped at V64 to seed,
 * then carried on to V66 — rather than the shared context, whose schema is already past the split.
 */
class PositionLocationBackfillMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static final UUID CITY_AND_COUNTRY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TWO_CITIES = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BARE_COUNTRY = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FREE_TEXT = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID BLANK = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID NOTHING = UUID.fromString("00000000-0000-0000-0000-000000000006");

    @Test
    @DisplayName("V66 reads each stored line back into the two halves the application now keeps")
    void backfillsTheHalvesFromTheOneLine() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "64");
            try (Connection connection = connect(postgres)) {
                seedMandates(connection);
            }

            migrateTo(postgres, "66");

            try (Connection connection = connect(postgres)) {
                assertThat(halvesOf(connection, CITY_AND_COUNTRY)).containsExactly("Riyadh", "Saudi Arabia");
                assertThat(halvesOf(connection, TWO_CITIES)).containsExactly("Sandton, Johannesburg", "South Africa");
                assertThat(halvesOf(connection, BARE_COUNTRY)).containsExactly(null, "United Arab Emirates");
                assertThat(halvesOf(connection, FREE_TEXT)).containsExactly("Remote", null);
                assertThat(halvesOf(connection, BLANK)).containsExactly(null, null);
                assertThat(halvesOf(connection, NOTHING)).containsExactly(null, null);

                assertThat(columnsOf(connection, "app_lm_position")).doesNotContain("location")
                        .contains("location_city", "location_country", "technical_share");
                assertThat(scalar(connection, "SELECT technical_share FROM app_lm_position WHERE id = ?", NOTHING))
                        .isEqualTo(50);
                assertThat(scalar(connection, """
                        SELECT numeric_precision FROM information_schema.columns
                        WHERE table_name = 'app_lm_position' AND column_name = 'bonus_value'""")).isEqualTo(14);
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

    private static void seedMandates(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'seed@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Seed Firm', 'seed-firm', 'example.com', '%s')""".formatted(WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, hq_country, created_by)
                    VALUES ('%s', '%s', 'Meridian Energy', 'United Arab Emirates', '%s')"""
                    .formatted(CLIENT, WORKSPACE, USER));
        }
        seedMandate(connection, CITY_AND_COUNTRY, "Riyadh, Saudi Arabia");
        seedMandate(connection, TWO_CITIES, "Sandton, Johannesburg, South Africa");
        seedMandate(connection, BARE_COUNTRY, "United Arab Emirates");
        seedMandate(connection, FREE_TEXT, "Remote");
        seedMandate(connection, BLANK, "");
        seedMandate(connection, NOTHING, null);
    }

    private static void seedMandate(Connection connection, UUID positionId, String location) throws SQLException {
        UUID projectId = UUID.randomUUID();
        try (PreparedStatement project = connection.prepareStatement("""
                INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                VALUES (?, ?, ?, 'Chief Financial Officer', ?)""");
             PreparedStatement position = connection.prepareStatement(
                     "INSERT INTO app_lm_position (id, project_id, location) VALUES (?, ?, ?)")) {
            project.setObject(1, projectId);
            project.setObject(2, WORKSPACE);
            project.setObject(3, CLIENT);
            project.setObject(4, USER);
            project.execute();
            position.setObject(1, positionId);
            position.setObject(2, projectId);
            position.setString(3, location);
            position.execute();
        }
    }

    private static List<String> halvesOf(Connection connection, UUID positionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT location_city, location_country FROM app_lm_position WHERE id = ?")) {
            statement.setObject(1, positionId);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                return Arrays.asList(row.getString(1), row.getString(2));
            }
        }
    }

    private static List<String> columnsOf(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> columns = new java.util.ArrayList<>();
                while (rows.next()) {
                    columns.add(rows.getString(1));
                }
                return columns;
            }
        }
    }

    private static Object scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getObject(1);
            }
        }
    }
}
