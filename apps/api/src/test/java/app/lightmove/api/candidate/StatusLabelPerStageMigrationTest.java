package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * V226: a status's name is unique within its stage, and Withdrew joins Shortlist and Offer — in a workspace founded
 * from then, and in an existing one only where it still runs V163's shape (a live Withdrew under Assessment), on a
 * live stage, under a key nobody holds,
 * and with nothing renamed. Its own container, stopped at V225 to seed, for
 * {@code CandidatePersonBackfillMigrationTest}'s reason.
 */
class StatusLabelPerStageMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000226a1");
    private static final UUID CURRENT = UUID.fromString("00000000-0000-0000-0000-0000000226b1");
    private static final UUID KEY_TAKEN = UUID.fromString("00000000-0000-0000-0000-0000000226b2");
    private static final UUID OFFER_RETIRED = UUID.fromString("00000000-0000-0000-0000-0000000226b3");
    private static final UUID NO_ASSESSMENT_WITHDREW = UUID.fromString("00000000-0000-0000-0000-0000000226b4");
    private static final UUID WITHDREW_RETIRED = UUID.fromString("00000000-0000-0000-0000-0000000226b6");
    private static final UUID FOUNDED_LATER = UUID.fromString("00000000-0000-0000-0000-0000000226b5");

    @Test
    @DisplayName("V226 scopes a status's name to its stage and adds Withdrew to Shortlist and Offer only where safe")
    void addsWithdrewOnlyWhereSafe() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "225");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "226");

            try (Connection connection = connect(postgres)) {
                assertThat(withdrewKeys(connection, CURRENT))
                        .containsExactly("interested:withdrew:8", "qualified:withdrewShortlist:6",
                                "shortlisted:withdrewOffer:6");
                assertThat(strings(connection, """
                        SELECT label FROM app_lm_workspace_candidate_status
                        WHERE workspace_id = ? AND key = 'outOfScope'""", CURRENT))
                        .as("an existing workspace is never renamed")
                        .containsExactly("Out of scope");
                assertThat(withdrewKeys(connection, KEY_TAKEN))
                        .as("Offer's key is already a status of the workspace's own")
                        .containsExactly("interested:withdrew:8", "qualified:withdrewShortlist:6");
                assertThat(withdrewKeys(connection, OFFER_RETIRED))
                        .containsExactly("interested:withdrew:8", "qualified:withdrewShortlist:6");
                assertThat(withdrewKeys(connection, NO_ASSESSMENT_WITHDREW)).isEmpty();
                assertThat(withdrewKeys(connection, WITHDREW_RETIRED))
                        .as("an admin retired Withdrew: it is not offered again at Shortlist or Offer")
                        .containsExactly("interested:withdrew:8");

                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                            VALUES ('%s', 'Later Firm', 'later-firm', 'example.com', '%s')"""
                            .formatted(FOUNDED_LATER, USER));
                    statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(FOUNDED_LATER));
                }
                assertThat(strings(connection, """
                        SELECT label FROM app_lm_workspace_candidate_status
                        WHERE workspace_id = ? AND key = 'outOfScope'""", FOUNDED_LATER))
                        .containsExactly("Not approached");
                assertThat(withdrewKeys(connection, FOUNDED_LATER))
                        .containsExactly("interested:withdrew:8", "qualified:withdrewShortlist:6",
                                "shortlisted:withdrewOffer:6");

                assertThatThrownBy(() -> addStatus(connection, CURRENT, "shortlisted", "lateWithdrawal", "WITHDREW"))
                        .as("one name once per stage, whatever its case")
                        .hasMessageContaining("app_lm_workspace_candidate_status_stage_label_uk");
                addStatus(connection, CURRENT, "contacted", "withdrewAtApproach", "Withdrew");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'withdrew@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Current Firm', 'current-firm', 'example.com', '%s'),
                           ('%s', 'Key Taken Firm', 'key-taken-firm', 'example.com', '%s'),
                           ('%s', 'Offer Retired Firm', 'offer-retired-firm', 'example.com', '%s'),
                           ('%s', 'Reshaped Firm', 'reshaped-firm', 'example.com', '%s'),
                           ('%s', 'Retired Withdrew Firm', 'retired-withdrew-firm', 'example.com', '%s')"""
                    .formatted(CURRENT, USER, KEY_TAKEN, USER, OFFER_RETIRED, USER,
                            NO_ASSESSMENT_WITHDREW, USER, WITHDREW_RETIRED, USER));
            for (UUID workspace : List.of(CURRENT, KEY_TAKEN, OFFER_RETIRED, NO_ASSESSMENT_WITHDREW,
                    WITHDREW_RETIRED)) {
                statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(workspace));
            }
            statement.execute("""
                    UPDATE app_lm_workspace_candidate_stage SET retired_at = now()
                    WHERE workspace_id = '%s' AND key = 'shortlisted'""".formatted(OFFER_RETIRED));
            statement.execute("""
                    DELETE FROM app_lm_workspace_candidate_status
                    WHERE workspace_id = '%s' AND key = 'withdrew'""".formatted(NO_ASSESSMENT_WITHDREW));
            statement.execute("""
                    UPDATE app_lm_workspace_candidate_status SET retired_at = now()
                    WHERE workspace_id = '%s' AND key = 'withdrew'""".formatted(WITHDREW_RETIRED));
        }
        addStatus(connection, KEY_TAKEN, "shortlisted", "withdrewOffer", "Withdrew offer");
    }

    private static void addStatus(Connection connection, UUID workspace, String stageKey, String key, String label)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_workspace_candidate_status
                    (workspace_id, stage_id, key, label, position, out_of_process)
                SELECT ?, id, ?, ?, 6, true FROM app_lm_workspace_candidate_stage
                WHERE workspace_id = ? AND key = ?""")) {
            statement.setObject(1, workspace);
            statement.setString(2, key);
            statement.setString(3, label);
            statement.setObject(4, workspace);
            statement.setString(5, stageKey);
            statement.execute();
        }
    }

    private static List<String> withdrewKeys(Connection connection, UUID workspace) throws SQLException {
        return strings(connection, """
                SELECT g.key || ':' || s.key || ':' || s.position
                FROM app_lm_workspace_candidate_status s
                JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id
                WHERE s.workspace_id = ? AND lower(s.label) = 'withdrew' AND s.out_of_process
                ORDER BY g.position""", workspace);
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
