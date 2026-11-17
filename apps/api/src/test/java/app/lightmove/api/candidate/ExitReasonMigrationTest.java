package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V227: every workspace whose pipeline exists gets the starting reasons, each linked only to the live ways out it
 * belongs to; a workspace with no pipeline yet gets none until its first read seeds them, and a second seed adds
 * nothing. Its own container, stopped at V226 to seed, for {@code CandidatePersonBackfillMigrationTest}'s reason.
 */
class ExitReasonMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000227a1");
    private static final UUID SEEDED = UUID.fromString("00000000-0000-0000-0000-0000000227b1");
    private static final UUID RETIRED_WAY_OUT = UUID.fromString("00000000-0000-0000-0000-0000000227b2");
    private static final UUID NO_PIPELINE = UUID.fromString("00000000-0000-0000-0000-0000000227b3");

    @Test
    @DisplayName("V227 seeds the reasons of every workspace with a pipeline, linked to its live ways out")
    void seedsLinkedReasons() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "226");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "227");

            try (Connection connection = connect(postgres)) {
                assertThat(count(connection, "SELECT count(*) FROM app_lm_workspace_exit_reason WHERE workspace_id = ?",
                        SEEDED)).isEqualTo(13);
                assertThat(offered(connection, SEEDED, "withdrewOffer")).isEqualTo("anotherOffer,compensation,other");
                assertThat(offered(connection, SEEDED, "notInterested"))
                        .isEqualTo("company,compensation,location,other,scope,timing");
                assertThat(offered(connection, SEEDED, "offLimits")).isNull();
                assertThat(offered(connection, RETIRED_WAY_OUT, "notProgressed"))
                        .as("a retired way out is linked to nothing").isNull();
                assertThat(count(connection, "SELECT count(*) FROM app_lm_workspace_exit_reason WHERE workspace_id = ?",
                        NO_PIPELINE)).isZero();

                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(NO_PIPELINE));
                    statement.execute("SELECT app_lm_seed_exit_reasons('%s')".formatted(NO_PIPELINE));
                    statement.execute("SELECT app_lm_seed_exit_reasons('%s')".formatted(NO_PIPELINE));
                }
                assertThat(count(connection, "SELECT count(*) FROM app_lm_workspace_exit_reason WHERE workspace_id = ?",
                        NO_PIPELINE)).isEqualTo(13);
                assertThat(offered(connection, NO_PIPELINE, "withdrewShortlist")).isEqualTo("anotherOffer,compensation,other");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'reasons@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Seeded Firm', 'seeded-firm', 'example.com', '%s'),
                           ('%s', 'Retired Firm', 'retired-firm', 'example.com', '%s'),
                           ('%s', 'Later Firm', 'later-firm', 'example.com', '%s')"""
                    .formatted(SEEDED, USER, RETIRED_WAY_OUT, USER, NO_PIPELINE, USER));
            for (UUID workspace : List.of(SEEDED, RETIRED_WAY_OUT)) {
                statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(workspace));
            }
            statement.execute("""
                    UPDATE app_lm_workspace_candidate_status SET retired_at = now()
                    WHERE workspace_id = '%s' AND key = 'notProgressed'""".formatted(RETIRED_WAY_OUT));
        }
    }

    private static String offered(Connection connection, UUID workspace, String statusKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT string_agg(reason.key, ',' ORDER BY reason.key)
                FROM app_lm_workspace_status_exit_reason link
                JOIN app_lm_workspace_exit_reason reason ON reason.id = link.reason_id
                JOIN app_lm_workspace_candidate_status status ON status.id = link.status_id
                WHERE status.workspace_id = ? AND status.key = ?""")) {
            statement.setObject(1, workspace);
            statement.setString(2, statusKey);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static long count(Connection connection, String sql, UUID workspace) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, workspace);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
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
