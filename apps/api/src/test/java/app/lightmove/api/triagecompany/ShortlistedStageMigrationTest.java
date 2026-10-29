package app.lightmove.api.triagecompany;

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
 * V160: the Shortlisted stage goes with what was left at it — an executive listed there is removed from the
 * position (the person kept, an UNMAPPED line written), a company there is removed as Remove from position does,
 * and an executive moved alone off it keeps their mapping unassigned. Its own container, stopped at V130 to seed
 * mappings V131 and V150 then backfill, and at V159 to move people alone.
 */
class ShortlistedStageMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000016a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000016b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000016c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000016d1");
    private static final UUID KEPT = UUID.fromString("00000000-0000-0000-0000-0000000016e1");
    private static final UUID SHORT = UUID.fromString("00000000-0000-0000-0000-0000000016e2");
    private static final UUID DECLINED = UUID.fromString("00000000-0000-0000-0000-0000000016e3");
    private static final UUID AT_SHORT = UUID.fromString("00000000-0000-0000-0000-0000000016f1");
    private static final UUID MOVED_OFF = UUID.fromString("00000000-0000-0000-0000-0000000016f2");
    private static final UUID MOVED_ON = UUID.fromString("00000000-0000-0000-0000-0000000016f3");
    private static final UUID AT_DECLINED = UUID.fromString("00000000-0000-0000-0000-0000000016f4");

    @Test
    @DisplayName("V160 removes what stood at Shortlisted and holds both stage columns to the two left")
    void discardsShortlisted() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "130");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }
            migrateTo(postgres, "159");
            try (Connection connection = connect(postgres)) {
                moveAlone(connection, MOVED_OFF, "IN_UNIVERSE");
                moveAlone(connection, MOVED_ON, "SHORTLISTED");
                companyNote(connection, KEPT);
                companyNote(connection, SHORT);
                execute(connection, """
                        INSERT INTO app_lm_candidate_scorecard (workspace_id, project_id, candidate_id)
                        VALUES ('%s', '%s', '%s')""".formatted(WORKSPACE, PROJECT, AT_SHORT));
                execute(connection, """
                        INSERT INTO app_lm_task (workspace_id, project_id, person_id, candidate_id, title, kind,
                                                 assignee_user_id, source)
                        SELECT '%s', '%s', person_id, id, 'Call back', 'CALL', '%s', 'MANUAL'
                        FROM app_lm_project_candidate WHERE id = '%s'""".formatted(WORKSPACE, PROJECT, USER, AT_SHORT));
            }

            migrateTo(postgres, "160");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT id::text || '|' || status FROM app_lm_project_triage_company
                        WHERE project_id = ? ORDER BY id""", PROJECT))
                        .containsExactly(KEPT + "|IN_UNIVERSE", DECLINED + "|DECLINED");
                assertThat(strings(connection, """
                        SELECT c.id::text || '|' || c.triage_status || '|' || coalesce(c.triage_company_id::text, '-')
                               || '|' || c.company_name
                        FROM app_lm_project_candidate c WHERE c.project_id = ? ORDER BY c.id""", PROJECT))
                        .containsExactly(
                                MOVED_OFF + "|IN_UNIVERSE|-|Harbour Ports",
                                AT_DECLINED + "|DECLINED|" + DECLINED + "|Desert Mining");
                assertThat(strings(connection, """
                        SELECT p.full_name || '|' || a.kind || '|' || a.project_title FROM app_lm_person_activity a
                        JOIN app_lm_person p ON p.id = a.person_id
                        WHERE a.project_id = ? AND a.kind = 'UNMAPPED' ORDER BY p.full_name""", PROJECT))
                        .containsExactly("Moved On|UNMAPPED|Chief Financial Officer",
                                "Short Listed|UNMAPPED|Chief Financial Officer");
                assertThat(strings(connection, "SELECT count(*)::text FROM app_lm_person WHERE workspace_id = ?",
                        WORKSPACE)).containsExactly("4");
                assertThat(strings(connection, """
                        SELECT triage_company_id::text FROM app_lm_company_note WHERE project_id = ?""", PROJECT))
                        .containsExactly(KEPT.toString());
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_candidate_scorecard WHERE project_id = ?""", PROJECT))
                        .containsExactly("0");
                assertThat(strings(connection, """
                        SELECT title || '|' || coalesce(candidate_id::text, '-') FROM app_lm_task
                        WHERE project_id = ?""", PROJECT))
                        .containsExactly("Call back|-");

                assertThatThrownBy(() -> execute(connection, """
                        UPDATE app_lm_project_triage_company SET status = 'SHORTLISTED' WHERE id = '%s'"""
                        .formatted(KEPT)))
                        .hasMessageContaining("app_lm_project_triage_company_status_chk");
                assertThatThrownBy(() -> execute(connection, """
                        UPDATE app_lm_project_candidate SET triage_status = 'SHORTLISTED' WHERE id = '%s'"""
                        .formatted(MOVED_OFF)))
                        .hasMessageContaining("app_lm_project_candidate_triage_status_chk");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'seed@example.com', 'Seed User')"""
                .formatted(USER));
        execute(connection, """
                INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                VALUES ('%s', 'Seed Firm', 'seed-firm', 'example.com', '%s')""".formatted(WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                .formatted(PROJECT, WORKSPACE, CLIENT, USER));
        company(connection, KEPT, "Gulf Industrial", "IN_UNIVERSE");
        company(connection, SHORT, "Harbour Ports", "SHORTLISTED");
        company(connection, DECLINED, "Desert Mining", "DECLINED");
        candidate(connection, AT_SHORT, "Short Listed", SHORT, "Harbour Ports");
        candidate(connection, MOVED_OFF, "Moved Off", SHORT, "Harbour Ports");
        candidate(connection, MOVED_ON, "Moved On", KEPT, "Gulf Industrial");
        candidate(connection, AT_DECLINED, "At Declined", DECLINED, "Desert Mining");
    }

    private static void company(Connection connection, UUID id, String name, String status) throws SQLException {
        execute(connection, """
                INSERT INTO app_lm_project_triage_company (id, project_id, source, status, company_name, added_by)
                VALUES ('%s', '%s', 'MANUAL', '%s', '%s', '%s')""".formatted(id, PROJECT, status, name, USER));
    }

    private static void candidate(Connection connection, UUID id, String fullName, UUID company, String companyName)
            throws SQLException {
        UUID person = UUID.randomUUID();
        execute(connection, """
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES ('%s', '%s', '%s', 'MANUAL', '%s')""".formatted(person, WORKSPACE, fullName, USER));
        execute(connection, """
                INSERT INTO app_lm_project_candidate (id, project_id, person_id, status, added_by, triage_company_id,
                                                      company_name)
                VALUES ('%s', '%s', '%s', 'IDENTIFIED', '%s', '%s', '%s')"""
                .formatted(id, PROJECT, person, USER, company, companyName));
    }

    private static void moveAlone(Connection connection, UUID candidate, String stage) throws SQLException {
        execute(connection, "UPDATE app_lm_project_candidate SET triage_status = '%s' WHERE id = '%s'"
                .formatted(stage, candidate));
    }

    private static void companyNote(Connection connection, UUID company) throws SQLException {
        execute(connection, """
                INSERT INTO app_lm_company_note (workspace_id, project_id, triage_company_id, kind, body,
                                                 author_user_id)
                VALUES ('%s', '%s', '%s', 'GENERAL', 'Met their CFO.', '%s')"""
                .formatted(WORKSPACE, PROJECT, company, USER));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
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
