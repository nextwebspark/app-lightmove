package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.text.service.LinkedInUrls;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V92 stores each person's profile slug exactly as {@link LinkedInUrls} reads it — the key a filing
 * looks them up by — and removes nothing: V91's frozen copies wait for the final cleanup migration.
 *
 * <p>Its own container and Flyway run, stopped at V91 to seed and carried on to V92, for
 * {@link CandidatePersonBackfillMigrationTest}'s reason.
 */
class PersonProfileSlugMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MANDATE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    /** Every spelling a researcher, a spreadsheet or the plugin has been seen to store. */
    private static final List<String> URLS = List.of(
            "https://www.linkedin.com/in/Slug-Same/",
            "https://ae.linkedin.com/in/j%C3%A9r%C3%B4me-d?trk=public_profile",
            "HTTPS://LINKEDIN.COM/in/UPPER",
            "  https://linkedin.com/in/trimmed  ",
            "https://www.linkedin.com:443/in/ported",
            "https://someone@www.linkedin.com/in/userinfo",
            "https://www.linkedin.com/in/split%2Fhere",
            "https://www.linkedin.com/in/fragment#about",
            "https://www.linkedin.com/in/",
            "https://www.linkedin.com/company/acme",
            "https://www.linkedin.com/search/results/people/?keywords=cfo",
            "https://www.linkedin.com/in/has space",
            "https://www.linkedin.com/in/bad%zzescape",
            "linkedin.com/in/no-scheme",
            "https://notlinkedin.com/in/impostor",
            "not a url at all");

    @Test
    @DisplayName("V92 stores each person's slug as LinkedInUrls reads it, and leaves V91's frozen copies alone")
    void storesTheProfileSlug() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "91");
            Map<UUID, String> urls = new LinkedHashMap<>();
            UUID older;
            UUID younger;
            UUID elsewhere;
            UUID mapping;
            try (Connection connection = connect(postgres)) {
                seedWorkspaces(connection);
                for (int index = 0; index < URLS.size(); index++) {
                    UUID personId = id(100 + index);
                    person(connection, personId, WORKSPACE, URLS.get(index), index);
                    urls.put(personId, URLS.get(index));
                }
                // One profile held twice in one workspace — V91 splits a chain that reaches one mandate
                // twice — and once more in another workspace, which is none of this one's business.
                older = id(201);
                younger = id(202);
                elsewhere = id(203);
                person(connection, older, WORKSPACE, "https://www.linkedin.com/in/held-twice", 100);
                person(connection, younger, WORKSPACE, "https://linkedin.com/in/Held-Twice/", 101);
                person(connection, elsewhere, OTHER_WORKSPACE, "https://www.linkedin.com/in/held-twice", 102);
                mapping = id(301);
                mapping(connection, mapping, older);
            }

            migrateTo(postgres, "92");

            try (Connection connection = connect(postgres)) {
                for (Map.Entry<UUID, String> entry : urls.entrySet()) {
                    assertThat(slugOf(connection, entry.getKey()))
                            .as("slug of %s", entry.getValue())
                            .isEqualTo(LinkedInUrls.profileSlugOrNull(entry.getValue()));
                }
                // The comparison above is only as good as its fixture: the tricky spellings must name a profile.
                assertThat(slugOf(connection, id(101))).isEqualTo("jérôme-d");
                assertThat(slugOf(connection, id(106))).isEqualTo("split");
                assertThat(slugOf(connection, id(102))).isEqualTo("upper");

                assertThat(slugOf(connection, older)).isEqualTo("held-twice");
                assertThat(slugOf(connection, younger)).isNull();
                assertThat(strings(connection, "SELECT linkedin_url FROM app_lm_person WHERE id = ?", younger))
                        .containsExactly("https://linkedin.com/in/Held-Twice/");
                assertThat(slugOf(connection, elsewhere)).isEqualTo("held-twice");

                assertThat(strings(connection, """
                        SELECT person_id::text || ' ' || status || ' ' || note || ' ' || (custom_fields ->> 'ethnicity')
                               || ' ' || full_name || ' ' || base_salary || ' ' || compensation_currency
                        FROM app_lm_project_candidate WHERE id = ?""", mapping))
                        .containsExactly(older + " ENGAGED Keep this note. Arab Stale Copy 900 USD");
                assertThat(scalar(connection, "SELECT count(*) FROM app_lm_candidate_contact WHERE candidate_id = ?", mapping))
                        .isEqualTo(1L);
                assertThat(scalar(connection, "SELECT count(*) FROM app_lm_candidate_photo WHERE candidate_id = ?", mapping))
                        .isEqualTo(1L);
                assertThat(strings(connection, """
                        SELECT full_name || ' ' || coalesce(base_salary::text, 'none') FROM app_lm_person WHERE id = ?""",
                        older)).containsExactly("Person 100 none");
            }
        }
    }

    private static void seedWorkspaces(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'seed@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Seed Firm', 'seed-firm', 'example.com', '%s'),
                           ('%s', 'Other Firm', 'other-firm', 'other.example', '%s')"""
                    .formatted(WORKSPACE, USER, OTHER_WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(MANDATE, WORKSPACE, CLIENT, USER));
        }
    }

    private static void person(Connection connection, UUID id, UUID workspaceId, String linkedinUrl, int minute)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, linkedin_url, source, created_by, created_at)
                VALUES (?, ?, ?, ?, 'MANUAL', ?, timestamptz '2020-01-01 00:00Z' + make_interval(mins => ?))""")) {
            statement.setObject(1, id);
            statement.setObject(2, workspaceId);
            statement.setString(3, "Person " + minute);
            statement.setString(4, linkedinUrl);
            statement.setObject(5, USER);
            statement.setInt(6, minute);
            statement.execute();
        }
    }

    /** A mapping still carrying V91's frozen copy of the person, which V92 must leave as it found it. */
    private static void mapping(Connection connection, UUID id, UUID personId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_project_candidate (id, project_id, person_id, full_name, base_salary,
                                                          compensation_currency, status, note, custom_fields, added_by)
                    VALUES ('%s', '%s', '%s', 'Stale Copy', 900, 'USD', 'ENGAGED', 'Keep this note.',
                            '{"ethnicity": "Arab"}', '%s')""".formatted(id, MANDATE, personId, USER));
            statement.execute("""
                    INSERT INTO app_lm_candidate_contact (candidate_id, channel, value, value_key, source)
                    VALUES ('%s', 'EMAIL', 'stale@x.example', 'stale@x.example', 'MANUAL')""".formatted(id));
            statement.execute("""
                    INSERT INTO app_lm_candidate_photo (candidate_id, content, content_type)
                    VALUES ('%s', '\\x01', 'image/jpeg')""".formatted(id));
        }
    }

    private static String slugOf(Connection connection, UUID personId) throws SQLException {
        return (String) scalar(connection, "SELECT profile_slug FROM app_lm_person WHERE id = ?", personId);
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
