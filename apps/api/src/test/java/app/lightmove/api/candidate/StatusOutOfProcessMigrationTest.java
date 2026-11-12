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
 * V163: every status of a closed stage is flagged out of process and loses its default mark, a workspace founded
 * from then is seeded with the five-stage catalog, and a workspace still holding V131's seed untouched — nobody at
 * any of it, no automation naming it — starts over on that catalog while every other is left as it stands. Its own
 * container, stopped at V162 to seed, for {@code CandidatePersonBackfillMigrationTest}'s reason.
 */
class StatusOutOfProcessMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000003a1");
    private static final UUID UNTOUCHED = UUID.fromString("00000000-0000-0000-0000-0000000003b1");
    private static final UUID HELD = UUID.fromString("00000000-0000-0000-0000-0000000003b2");
    private static final UUID RENAMED = UUID.fromString("00000000-0000-0000-0000-0000000003b3");
    private static final UUID AUTOMATED = UUID.fromString("00000000-0000-0000-0000-0000000003b4");
    private static final UUID FOUNDED_LATER = UUID.fromString("00000000-0000-0000-0000-0000000003b5");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000003c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000003d1");

    private static final String OLD_STAGES = "Identified,Contacted,Interested,Qualified,Shortlisted,Declined";
    private static final String NEW_STAGES = "Research,Approach,Assessment,Shortlist,Offer";

    @Test
    @DisplayName("V163 flags the closed stage's statuses, and reseeds only a catalog nobody touched or stands on")
    void reseedsOnlyAnUntouchedCatalog() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "162");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "163");

            try (Connection connection = connect(postgres)) {
                assertThat(stageLabels(connection, UNTOUCHED)).isEqualTo(NEW_STAGES);
                for (UUID kept : List.of(HELD, RENAMED, AUTOMATED)) {
                    assertThat(stageLabels(connection, kept)).as("workspace %s", kept).isEqualTo(OLD_STAGES);
                    assertThat(strings(connection, """
                            SELECT g.kind || '|' || bool_and(s.out_of_process) || '|' || bool_or(s.out_of_process)
                                   || '|' || bool_or(s.is_default)
                            FROM app_lm_workspace_candidate_status s
                            JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id
                            WHERE s.workspace_id = ? GROUP BY g.kind ORDER BY g.kind""", kept))
                            .as("the closed stage's statuses flagged, and none of them a default any more")
                            .containsExactly("ACTIVE|false|false|true", "CLOSED|true|true|false");
                }
                assertThat(strings(connection, """
                        SELECT s.key FROM app_lm_project_candidate c
                        JOIN app_lm_workspace_candidate_status s ON s.id = c.status_id
                        WHERE c.project_id = ?""", PROJECT))
                        .containsExactly("identified");

                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                            VALUES ('%s', 'Later Firm', 'later-firm', 'example.com', '%s')"""
                            .formatted(FOUNDED_LATER, USER));
                    statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(FOUNDED_LATER));
                }
                assertThat(stageLabels(connection, FOUNDED_LATER)).isEqualTo(NEW_STAGES);
                assertThat(strings(connection, """
                        SELECT string_agg(s.key, ',' ORDER BY g.position, s.position)
                        FROM app_lm_workspace_candidate_status s
                        JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id
                        WHERE s.workspace_id = ? AND s.out_of_process""", FOUNDED_LATER))
                        .containsExactly("outOfScope,offLimits,notInterested,unresponsive,screenedOut,notProgressed,"
                                + "withdrew,clientRejected,clientRejectedAfterReferences,declinedOffer");
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_workspace_candidate_status
                        WHERE workspace_id = ? AND system_key IS NOT NULL""", FOUNDED_LATER))
                        .containsExactly("7");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'reseed@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Untouched Firm', 'untouched-firm', 'example.com', '%s'),
                           ('%s', 'Held Firm', 'held-firm', 'example.com', '%s'),
                           ('%s', 'Renamed Firm', 'renamed-firm', 'example.com', '%s'),
                           ('%s', 'Automated Firm', 'automated-firm', 'example.com', '%s')"""
                    .formatted(UNTOUCHED, USER, HELD, USER, RENAMED, USER, AUTOMATED, USER));
            for (UUID workspace : List.of(UNTOUCHED, HELD, RENAMED, AUTOMATED)) {
                statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(workspace));
            }
            statement.execute("""
                    UPDATE app_lm_workspace_candidate_status SET label = 'Long list'
                    WHERE workspace_id = '%s' AND key = 'researching'""".formatted(RENAMED));
            statement.execute("""
                    INSERT INTO app_lm_workspace_status_automation (workspace_id, trigger, status_id, enabled)
                    VALUES ('%s', 'REPLIED', NULL, false)""".formatted(AUTOMATED));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, HELD, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(PROJECT, HELD, CLIENT, USER));
        }
        UUID person = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES (?, ?, 'Held Person', 'MANUAL', ?)""")) {
            statement.setObject(1, person);
            statement.setObject(2, HELD);
            statement.setObject(3, USER);
            statement.execute();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_candidate (project_id, person_id, source, added_by)
                VALUES (?, ?, 'MANUAL', ?)""")) {
            statement.setObject(1, PROJECT);
            statement.setObject(2, person);
            statement.setObject(3, USER);
            statement.execute();
        }
    }

    private static String stageLabels(Connection connection, UUID workspace) throws SQLException {
        return strings(connection, """
                SELECT string_agg(label, ',' ORDER BY position) FROM app_lm_workspace_candidate_stage
                WHERE workspace_id = ?""", workspace).getFirst();
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
