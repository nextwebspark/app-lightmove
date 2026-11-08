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
 * V195: a workspace still holding V131's closed Declined stage loses it — each of its statuses moves under the stage
 * it is usually given at, and whoever left at another stage, by their status history, gets that status under that
 * stage — while a closed stage an admin gave a status in process is left as it stands. Its own container, stopped
 * at V162 to seed a pre-V163 catalog, for {@code CandidatePersonBackfillMigrationTest}'s reason.
 */
class ClosedStageDissolvedMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000005a1");
    private static final UUID LEGACY = UUID.fromString("00000000-0000-0000-0000-0000000005b1");
    private static final UUID REOPENED = UUID.fromString("00000000-0000-0000-0000-0000000005b2");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000005c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000005d1");
    private static final UUID REOPENED_CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000005c2");
    private static final UUID REOPENED_PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000005d2");

    @Test
    @DisplayName("V195 files the old Declined stage's people under the stage they left at, and drops the stage")
    void dissolvesTheClosedStage() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "162");
            Map<String, UUID> people;
            try (Connection connection = connect(postgres)) {
                people = seed(connection);
            }
            migrateTo(postgres, "194");
            try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO app_lm_workspace_candidate_status
                            (workspace_id, stage_id, key, label, colour, position, is_default, out_of_process)
                        SELECT workspace_id, id, 'reconsidering', 'Reconsidering', 'RED', 8, false, false
                        FROM app_lm_workspace_candidate_stage WHERE workspace_id = '%s' AND key = 'declined'"""
                        .formatted(REOPENED));
            }

            migrateTo(postgres, "195");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT string_agg(label || '@' || position, ',' ORDER BY position)
                        FROM app_lm_workspace_candidate_stage WHERE workspace_id = ?""", LEGACY))
                        .containsExactly("Identified@1,Contacted@2,Interested@3,Qualified@4,Shortlisted@5");
                assertThat(strings(connection, """
                        SELECT g.key || ':' || string_agg(s.key, ',' ORDER BY s.position)
                        FROM app_lm_workspace_candidate_status s
                        JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id
                        WHERE s.workspace_id = ? AND s.out_of_process
                        GROUP BY g.key, g.position ORDER BY g.position""", LEGACY))
                        .containsExactly("identified:offLimits,outOfScope",
                                "contacted:notInterested,noResponse",
                                "interested:withdrew,noResponseInterested",
                                "qualified:notAFit,placedElsewhere",
                                "shortlisted:rejectedByClient,notInterestedShortlisted");
                assertThat(strings(connection, """
                        SELECT label || '|' || colour || '|' || is_default || '|' || (system_key IS NULL)
                        FROM app_lm_workspace_candidate_status
                        WHERE workspace_id = ? AND key IN ('notInterestedShortlisted', 'noResponseInterested')
                        ORDER BY key""", LEGACY))
                        .containsExactly("No response (Interested)|NEUTRAL|false|true",
                                "Not interested (Shortlisted)|NEUTRAL|false|true");

                assertThat(standing(connection, people.get("approached"))).isEqualTo("contacted:notInterested");
                assertThat(standing(connection, people.get("shortlisted")))
                        .isEqualTo("shortlisted:notInterestedShortlisted");
                assertThat(standing(connection, people.get("unrecorded"))).isEqualTo("shortlisted:rejectedByClient");
                assertThat(standing(connection, people.get("interested"))).isEqualTo("interested:noResponseInterested");
                assertThat(standing(connection, people.get("placed"))).isEqualTo("qualified:placedElsewhere");
                assertThat(standing(connection, people.get("inProcess"))).isEqualTo("contacted:contacted");

                assertThat(strings(connection, """
                        SELECT string_agg(label, ',' ORDER BY position)
                        FROM app_lm_workspace_candidate_stage WHERE workspace_id = ?""", REOPENED))
                        .containsExactly("Identified,Contacted,Interested,Qualified,Shortlisted,Declined");
            }
        }
    }

    /** Two pre-V163 workspaces, someone standing in the first so V163 keeps its catalog, and the first's people. */
    private static Map<String, UUID> seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'dissolve@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Legacy Firm', 'legacy-firm', 'example.com', '%s'),
                           ('%s', 'Reopened Firm', 'reopened-firm', 'example.com', '%s')"""
                    .formatted(LEGACY, USER, REOPENED, USER));
            for (UUID workspace : List.of(LEGACY, REOPENED)) {
                statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(workspace));
            }
            statement.execute("""
                    INSERT INTO app_lm_workspace_candidate_status
                        (workspace_id, stage_id, key, label, colour, position, is_default)
                    SELECT workspace_id, id, 'placedElsewhere', 'Placed elsewhere', 'NEUTRAL', 8, false
                    FROM app_lm_workspace_candidate_stage WHERE workspace_id = '%s' AND key = 'declined'"""
                    .formatted(LEGACY));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s'),
                           ('%s', '%s', 'Group Audit', '%s')"""
                    .formatted(CLIENT, LEGACY, USER, REOPENED_CLIENT, REOPENED, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s'),
                           ('%s', '%s', '%s', 'Chief Audit Officer', '%s')"""
                    .formatted(PROJECT, LEGACY, CLIENT, USER, REOPENED_PROJECT, REOPENED, REOPENED_CLIENT, USER));
        }
        map(connection, REOPENED, REOPENED_PROJECT, person(connection, REOPENED, "Reopened Person"), "notInterested");

        Map<String, UUID> people = Map.of(
                "approached", person(connection, LEGACY, "Approached Person"),
                "shortlisted", person(connection, LEGACY, "Shortlisted Person"),
                "unrecorded", person(connection, LEGACY, "Unrecorded Person"),
                "interested", person(connection, LEGACY, "Interested Person"),
                "placed", person(connection, LEGACY, "Placed Person"),
                "inProcess", person(connection, LEGACY, "In Process Person"));
        map(connection, LEGACY, PROJECT, people.get("approached"), "notInterested",
                "identified>contacted", "contacted>notInterested");
        map(connection, LEGACY, PROJECT, people.get("shortlisted"), "notInterested",
                "identified>shortlisted", "shortlisted>notInterested");
        map(connection, LEGACY, PROJECT, people.get("unrecorded"), "rejectedByClient");
        map(connection, LEGACY, PROJECT, people.get("interested"), "noResponse",
                "identified>interested", "interested>withdrew", "withdrew>noResponse");
        map(connection, LEGACY, PROJECT, people.get("placed"), "placedElsewhere", "qualified>placedElsewhere");
        map(connection, LEGACY, PROJECT, people.get("inProcess"), "contacted", "identified>contacted");
        return people;
    }

    private static UUID person(Connection connection, UUID workspace, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES (?, ?, ?, 'MANUAL', ?)""")) {
            statement.setObject(1, id);
            statement.setObject(2, workspace);
            statement.setString(3, name);
            statement.setObject(4, USER);
            statement.execute();
        }
        return id;
    }

    /** Maps the person at {@code statusKey}, with one status line per {@code from>to} change, oldest first. */
    private static void map(Connection connection, UUID workspace, UUID project, UUID person, String statusKey,
                            String... changes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_candidate (project_id, person_id, source, added_by, status_id)
                SELECT ?, ?, 'MANUAL', ?, id FROM app_lm_workspace_candidate_status
                WHERE workspace_id = ? AND key = ?""")) {
            statement.setObject(1, project);
            statement.setObject(2, person);
            statement.setObject(3, USER);
            statement.setObject(4, workspace);
            statement.setString(5, statusKey);
            statement.execute();
        }
        for (String change : changes) {
            String[] fromTo = change.split(">");
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, kind, details)
                    VALUES (?, ?, ?, 'STATUS_CHANGED', jsonb_build_object('from', ?::text, 'to', ?::text))""")) {
                statement.setObject(1, workspace);
                statement.setObject(2, person);
                statement.setObject(3, project);
                statement.setString(4, fromTo[0]);
                statement.setString(5, fromTo[1]);
                statement.execute();
            }
        }
    }

    private static String standing(Connection connection, UUID person) throws SQLException {
        return strings(connection, """
                SELECT g.key || ':' || s.key FROM app_lm_project_candidate c
                JOIN app_lm_workspace_candidate_status s ON s.id = c.status_id
                JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id
                WHERE c.person_id = ?""", person).getFirst();
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
