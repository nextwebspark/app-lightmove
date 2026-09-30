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
 * V91's backfill, run against rows written the way the application wrote them before it: one person
 * met on two mandates under two spellings of their profile, one address typed in two cases, one address
 * shared by two different profiles, a chain of matches reaching back into its own mandate, and two
 * people who only share a name.
 *
 * <p>Its own container and Flyway run, stopped at V90 to seed and carried on to V91, for
 * {@code PositionLocationBackfillMigrationTest}'s reason: the shared context's schema is already past it.
 * It stays pinned at V91 for good: V92 drops the mandate-row columns and the two tables it seeds.
 */
class CandidatePersonBackfillMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CFO = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID CREDIT = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID LEASING = UUID.fromString("00000000-0000-0000-0000-0000000000d3");

    private static final UUID SLUG_ON_CFO = id(1);
    private static final UUID SLUG_ON_CREDIT = id(2);
    private static final UUID EMAIL_ON_CFO = id(3);
    private static final UUID EMAIL_ON_LEASING = id(4);
    private static final UUID CONFLICT_ON_CFO = id(5);
    private static final UUID CONFLICT_ON_CREDIT = id(6);
    private static final UUID CHAIN_ON_CFO = id(7);
    private static final UUID CHAIN_ON_CREDIT = id(8);
    private static final UUID CHAIN_BACK_ON_CFO = id(9);
    private static final UUID NAMESAKE_ON_CFO = id(10);
    private static final UUID NAMESAKE_ON_CREDIT = id(11);
    private static final UUID FLAGGED_ON_CFO = id(12);
    private static final UUID FLAGGED_ON_CREDIT = id(13);
    private static final UUID SEARCH_URL_ON_CFO = id(14);
    private static final UUID CAPTURED_ON_CREDIT = id(15);

    @Test
    @DisplayName("V91 folds rows into people on a profile or an email, and never on a name")
    void foldsRowsIntoPeople() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "90");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "91");

            try (Connection connection = connect(postgres)) {
                assertThat(personOf(connection, SLUG_ON_CREDIT)).isEqualTo(SLUG_ON_CFO);
                assertThat(personOf(connection, EMAIL_ON_LEASING)).isEqualTo(EMAIL_ON_CFO);
                assertThat(personOf(connection, CONFLICT_ON_CREDIT)).isEqualTo(CONFLICT_ON_CREDIT);
                assertThat(personOf(connection, CHAIN_ON_CREDIT)).isEqualTo(CHAIN_ON_CFO);
                // The chain reaches back into the CFO mandate, which holds a person once.
                assertThat(personOf(connection, CHAIN_BACK_ON_CFO)).isEqualTo(CHAIN_BACK_ON_CFO);
                assertThat(personOf(connection, NAMESAKE_ON_CREDIT)).isEqualTo(NAMESAKE_ON_CREDIT);

                // The oldest row founds the person; later rows fill what it left empty, and the pay
                // comes whole from the one row that recorded it.
                assertThat(strings(connection, """
                        SELECT title, nationality, compensation_currency, base_salary::text,
                               linkedin_url_locked::text, source
                        FROM app_lm_person WHERE id = ?""", SLUG_ON_CFO))
                        .containsExactly("CFO", "Emirati", "AED", "100", "true", "EXTENSION");

                // Each value keeps the AI flag of the row it came from, and no flag outlives its value.
                assertThat(personOf(connection, FLAGGED_ON_CREDIT)).isEqualTo(FLAGGED_ON_CFO);
                assertThat(strings(connection, """
                        SELECT nationality, gender, years_experience::text,
                               (SELECT string_agg(f, ',' ORDER BY f) FROM jsonb_array_elements_text(ai_inferred_fields) f)
                        FROM app_lm_person WHERE id = ?""", FLAGGED_ON_CFO))
                        .containsExactly("Emirati", "MALE", "10", "gender,nationality");

                // A URL that names no profile gives way to the one a capture read, and only that is locked.
                assertThat(personOf(connection, CAPTURED_ON_CREDIT)).isEqualTo(SEARCH_URL_ON_CFO);
                assertThat(strings(connection, """
                        SELECT linkedin_url, linkedin_url_locked::text FROM app_lm_person WHERE id = ?""",
                        SEARCH_URL_ON_CFO))
                        .containsExactly("https://www.linkedin.com/in/locked-page", "true");

                // One address in two cases is one row, and the verified reading wins.
                assertThat(strings(connection, """
                        SELECT value || ':' || verified FROM app_lm_person_contact
                        WHERE person_id = ? AND channel = 'EMAIL'""", EMAIL_ON_CFO))
                        .containsExactly("SHARE@x.example:true");
                assertThat(strings(connection, """
                        SELECT encode(content, 'hex') FROM app_lm_person_photo WHERE person_id = ?""", SLUG_ON_CFO))
                        .containsExactly("01");

                assertThat(strings(connection, """
                        SELECT kind || ' ' || project_title || ' ' || actor_user_id
                        FROM app_lm_person_activity WHERE person_id = ? ORDER BY id""", SLUG_ON_CFO))
                        .containsExactly("ADDED_TO_POOL Chief Financial Officer " + USER,
                                "MAPPED Head of Credit Risk " + USER);

                assertThat(scalar(connection, "SELECT count(*) FROM app_lm_project_candidate WHERE person_id IS NULL"))
                        .isEqualTo(0L);
                assertThat(scalar(connection, "SELECT count(*) FROM app_lm_person")).isEqualTo(10L);
                // The previous revision still reads these while the deploy finishes.
                assertThat(scalar(connection, "SELECT count(*) FROM app_lm_candidate_contact")).isEqualTo(13L);
                assertThat(scalar(connection, "SELECT count(*) FROM app_lm_candidate_photo")).isEqualTo(2L);
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'seed@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Seed Firm', 'seed-firm', 'example.com', '%s')""".formatted(WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
        }
        mandate(connection, CFO, "Chief Financial Officer");
        mandate(connection, CREDIT, "Head of Credit Risk");
        mandate(connection, LEASING, "Leasing Director");

        candidate(connection, SLUG_ON_CFO, CFO, "Slug Same", "CFO", "https://www.linkedin.com/in/Slug-Same/", "EXTENSION", 1, null, null, null);
        candidate(connection, SLUG_ON_CREDIT, CREDIT, "Slug Same", null, "https://linkedin.com/in/slug-same?x=1", "MANUAL", 2, "Emirati", "AED", 100L);
        candidate(connection, EMAIL_ON_CFO, CFO, "Email Share", null, null, "MANUAL", 3, null, null, null);
        candidate(connection, EMAIL_ON_LEASING, LEASING, "Email Share Too", null, null, "CSV", 4, null, null, null);
        candidate(connection, CONFLICT_ON_CFO, CFO, "Conflict One", null, "https://www.linkedin.com/in/one", "MANUAL", 5, null, null, null);
        candidate(connection, CONFLICT_ON_CREDIT, CREDIT, "Conflict Two", null, "https://www.linkedin.com/in/two", "MANUAL", 6, null, null, null);
        candidate(connection, CHAIN_ON_CFO, CFO, "Chain P", null, "https://www.linkedin.com/in/chain", "MANUAL", 7, null, null, null);
        candidate(connection, CHAIN_ON_CREDIT, CREDIT, "Chain Q", null, null, "MANUAL", 8, null, null, null);
        candidate(connection, CHAIN_BACK_ON_CFO, CFO, "Chain R", null, null, "MANUAL", 9, null, null, null);
        candidate(connection, NAMESAKE_ON_CFO, CFO, "Name Only", null, null, "MANUAL", 10, null, null, null);
        candidate(connection, NAMESAKE_ON_CREDIT, CREDIT, "Name Only", null, null, "MANUAL", 11, null, null, null);
        candidate(connection, FLAGGED_ON_CFO, CFO, "Flagged", null, null, "MANUAL", 12, null, null, null);
        candidate(connection, FLAGGED_ON_CREDIT, CREDIT, "Flagged", null, null, "MANUAL", 13, "Emirati", null, null);
        candidate(connection, SEARCH_URL_ON_CFO, CFO, "Search Url", null,
                "https://www.linkedin.com/search/results/people/?keywords=locked", "MANUAL", 14, null, null, null);
        candidate(connection, CAPTURED_ON_CREDIT, CREDIT, "Search Url", null,
                "https://www.linkedin.com/in/locked-page", "EXTENSION", 15, null, null, null);

        email(connection, EMAIL_ON_CFO, "share@x.example", false, "MANUAL");
        email(connection, EMAIL_ON_LEASING, "SHARE@x.example", true, "CSV");
        email(connection, CONFLICT_ON_CFO, "c@x.example", false, "MANUAL");
        email(connection, CONFLICT_ON_CREDIT, "c@x.example", false, "MANUAL");
        email(connection, CHAIN_ON_CFO, "chain@x.example", false, "MANUAL");
        email(connection, CHAIN_ON_CREDIT, "chain@x.example", false, "MANUAL");
        email(connection, CHAIN_ON_CREDIT, "r@x.example", false, "MANUAL");
        email(connection, CHAIN_BACK_ON_CFO, "r@x.example", false, "MANUAL");
        email(connection, FLAGGED_ON_CFO, "flag@x.example", false, "MANUAL");
        email(connection, FLAGGED_ON_CREDIT, "flag@x.example", false, "MANUAL");
        email(connection, SEARCH_URL_ON_CFO, "lock@x.example", false, "MANUAL");
        email(connection, CAPTURED_ON_CREDIT, "lock@x.example", false, "EXTENSION");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_candidate_contact (candidate_id, channel, value, value_key, source)
                    VALUES ('%s', 'PHONE', '+971 50 1', '971501', 'CSV')""".formatted(EMAIL_ON_LEASING));
            statement.execute("""
                    INSERT INTO app_lm_candidate_photo (candidate_id, content, content_type)
                    VALUES ('%s', '\\x01', 'image/jpeg'), ('%s', '\\x02', 'image/png')"""
                    .formatted(SLUG_ON_CFO, SLUG_ON_CREDIT));
            // The oldest row's flags name a value it never held; the younger row's name the one it did.
            statement.execute("""
                    UPDATE app_lm_project_candidate SET gender = 'MALE', ai_inferred_fields = '["gender", "yearsExperience"]'
                    WHERE id = '%s'""".formatted(FLAGGED_ON_CFO));
            statement.execute("""
                    UPDATE app_lm_project_candidate SET gender = 'FEMALE', years_experience = 10,
                                                        ai_inferred_fields = '["nationality"]'
                    WHERE id = '%s'""".formatted(FLAGGED_ON_CREDIT));
        }
    }

    private static void mandate(Connection connection, UUID projectId, String title) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                VALUES (?, ?, ?, ?, ?)""")) {
            statement.setObject(1, projectId);
            statement.setObject(2, WORKSPACE);
            statement.setObject(3, CLIENT);
            statement.setString(4, title);
            statement.setObject(5, USER);
            statement.execute();
        }
    }

    private static void candidate(Connection connection, UUID id, UUID projectId, String name, String title,
                                  String linkedinUrl, String source, int minute, String nationality,
                                  String currency, Long baseSalary) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_candidate (id, project_id, full_name, title, linkedin_url, source,
                                                      added_by, created_at, nationality,
                                                      compensation_currency, base_salary)
                VALUES (?, ?, ?, ?, ?, ?, ?, timestamptz '2020-01-01 00:00Z' + make_interval(mins => ?), ?, ?, ?)""")) {
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setString(3, name);
            statement.setString(4, title);
            statement.setString(5, linkedinUrl);
            statement.setString(6, source);
            statement.setObject(7, USER);
            statement.setInt(8, minute);
            statement.setString(9, nationality);
            statement.setString(10, currency);
            statement.setObject(11, baseSalary);
            statement.execute();
        }
    }

    private static void email(Connection connection, UUID candidateId, String address, boolean verified,
                              String source) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_candidate_contact (candidate_id, channel, value, value_key, verified, source)
                VALUES (?, 'EMAIL', ?, lower(?), ?, ?)""")) {
            statement.setObject(1, candidateId);
            statement.setString(2, address);
            statement.setString(3, address);
            statement.setBoolean(4, verified);
            statement.setString(5, source);
            statement.execute();
        }
    }

    private static UUID personOf(Connection connection, UUID candidateId) throws SQLException {
        return (UUID) scalar(connection, "SELECT person_id FROM app_lm_project_candidate WHERE id = ?", candidateId);
    }

    private static List<String> strings(Connection connection, String sql, Object parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                int columns = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    for (int column = 1; column <= columns; column++) {
                        values.add(rows.getString(column));
                    }
                }
                return values;
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

    private static UUID id(int sequence) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(sequence));
    }
}
