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
 * V166: an executive left with a company name and no company is put back under the one company the position holds by
 * that name, at the stage they were on. Its own container, stopped at V165 to seed, for
 * {@code CandidatePersonBackfillMigrationTest}'s reason.
 */
class StrandedExecutivesMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000165a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000165b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000165c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000165d1");

    @Test
    @DisplayName("V166 re-attaches the stranded to the one same-named company at their own stage, and leaves every doubtful case alone")
    void reattachesOnlyWhereOneCompanyHoldsTheName() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "165");
            UUID futtaim;
            UUID spinneys;
            UUID lulu;
            try (Connection connection = connect(postgres)) {
                seedPosition(connection);
                futtaim = company(connection, "Al-Futtaim", "DECLINED", null, true);
                company(connection, "Twin Co", "IN_UNIVERSE", null, false);
                company(connection, "Twin Co", "IN_UNIVERSE", "apollo-twin", false);
                spinneys = company(connection, "Spinneys", "IN_UNIVERSE", null, false);
                lulu = company(connection, "Lulu", "IN_UNIVERSE", null, false);
                executive(connection, "Farooq Naveed", null, "Al-Futtaim");
                executive(connection, "Ummer Farook", null, " al-futtaim ");
                executive(connection, "Twin Person", null, "Twin Co");
                executive(connection, "Outside Person", null, "Outside Holdings");
                executive(connection, "Wei Ling Tan", spinneys, "Spinneys");
                executive(connection, "Wei Ling Tan", null, "Spinneys");
                executive(connection, "John Smith", null, "Lulu");
                executive(connection, "John Smith", null, "Lulu");
            }

            migrateTo(postgres, "166");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT p.full_name || '|' || coalesce(c.triage_company_id::text, '-') || '|' || c.triage_status
                               || '|' || coalesce(c.company_name, '-')
                        FROM app_lm_project_candidate c JOIN app_lm_person p ON p.id = c.person_id
                        WHERE c.project_id = ? ORDER BY p.full_name, c.triage_company_id NULLS LAST""", PROJECT))
                        .containsExactly(
                                "Farooq Naveed|" + futtaim + "|IN_UNIVERSE|Al-Futtaim",
                                "John Smith|" + lulu + "|IN_UNIVERSE|Lulu",
                                "John Smith|-|IN_UNIVERSE|Lulu",
                                "Outside Person|-|IN_UNIVERSE|Outside Holdings",
                                "Twin Person|-|IN_UNIVERSE|Twin Co",
                                "Ummer Farook|" + futtaim + "|IN_UNIVERSE|Al-Futtaim",
                                "Wei Ling Tan|" + spinneys + "|IN_UNIVERSE|Spinneys",
                                "Wei Ling Tan|-|IN_UNIVERSE|Spinneys");
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_person_activity WHERE project_id = ?""", PROJECT))
                        .containsExactly("0");
                assertThat(strings(connection, """
                        SELECT no_executive_found::text FROM app_lm_project_triage_company WHERE id = ?""", futtaim))
                        .containsExactly("false");
            }
        }
    }

    private static void seedPosition(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'strand@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Strand Firm', 'strand-firm', 'example.com', '%s')""".formatted(WORKSPACE, USER));
            statement.execute("SELECT app_lm_seed_candidate_pipeline('%s')".formatted(WORKSPACE));
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Retail', '%s')""".formatted(CLIENT, WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Merchandising Director', '%s')"""
                    .formatted(PROJECT, WORKSPACE, CLIENT, USER));
        }
    }

    private static UUID company(Connection connection, String name, String status, String apolloAccountId,
                                boolean noExecutiveFound) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_triage_company
                    (id, project_id, apollo_account_id, source, status, company_name, added_by, no_executive_found)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            statement.setObject(1, id);
            statement.setObject(2, PROJECT);
            statement.setString(3, apolloAccountId);
            statement.setString(4, apolloAccountId == null ? "MANUAL" : "STRATEGY");
            statement.setString(5, status);
            statement.setString(6, name);
            statement.setObject(7, USER);
            statement.setBoolean(8, noExecutiveFound);
            statement.execute();
        }
        return id;
    }

    private static void executive(Connection connection, String fullName, UUID triageCompanyId, String companyName)
            throws SQLException {
        UUID person = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES (?, ?, ?, 'MANUAL', ?)""")) {
            statement.setObject(1, person);
            statement.setObject(2, WORKSPACE);
            statement.setString(3, fullName);
            statement.setObject(4, USER);
            statement.execute();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_project_candidate (project_id, person_id, triage_company_id, company_name, added_by)
                VALUES (?, ?, ?, ?, ?)""")) {
            statement.setObject(1, PROJECT);
            statement.setObject(2, person);
            statement.setObject(3, triageCompanyId);
            statement.setString(4, companyName);
            statement.setObject(5, USER);
            statement.execute();
        }
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
