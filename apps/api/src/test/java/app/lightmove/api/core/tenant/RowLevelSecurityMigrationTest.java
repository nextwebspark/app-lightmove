package app.lightmove.api.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V166: the grid tables' rows already filed take their project's workspace, a row filed after takes it from the
 * project whatever the insert names, and none can claim a workspace its project is not in. Its own container,
 * stopped at V165 to seed, for {@code PersonNotesBackfillMigrationTest}'s reason; as the container's superuser,
 * above the policies, so the rows themselves are what is checked.
 */
class RowLevelSecurityMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000001a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000001b1");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000001b2");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000001c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000001d1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000001e1");
    private static final UUID FILED_COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000001f1");

    @Test
    @DisplayName("V166 gives the grid rows their project's workspace, then fills and holds it on every insert")
    void theGridRowsCarryTheirProjectsWorkspace() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "165");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "166");

            try (Connection connection = connect(postgres)) {
                assertThat(workspaceOf(connection, "app_lm_project_triage_company", FILED_COMPANY))
                        .isEqualTo(WORKSPACE);
                assertThat(single(connection, "SELECT workspace_id FROM app_lm_project_candidate WHERE person_id = ?",
                        PERSON)).isEqualTo(WORKSPACE);

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO app_lm_project_triage_company
                            (project_id, workspace_id, source, status, company_name, added_by)
                        VALUES (?, ?, 'MANUAL', 'IN_UNIVERSE', 'Harbour Ports', ?) RETURNING workspace_id""")) {
                    statement.setObject(1, PROJECT);
                    statement.setObject(2, OTHER_WORKSPACE);
                    statement.setObject(3, USER);
                    try (ResultSet inserted = statement.executeQuery()) {
                        inserted.next();
                        assertThat(inserted.getObject(1, UUID.class)).isEqualTo(WORKSPACE);
                    }
                }

                assertThatThrownBy(() -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("UPDATE app_lm_project_candidate SET workspace_id = '%s' WHERE person_id = '%s'"
                                .formatted(OTHER_WORKSPACE, PERSON));
                    }
                }).isInstanceOf(SQLException.class).hasMessageContaining("project_workspace_fk");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'seed@example.com', 'Seed User')"""
                    .formatted(USER));
            for (UUID workspace : new UUID[] {WORKSPACE, OTHER_WORKSPACE}) {
                statement.execute("""
                        INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                        VALUES ('%s', 'Firm %s', 'firm-%s', 'example.com', '%s')"""
                        .formatted(workspace, workspace, workspace, USER));
            }
            statement.execute("""
                    INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                    VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                    VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')"""
                    .formatted(PROJECT, WORKSPACE, CLIENT, USER));
            statement.execute("""
                    INSERT INTO app_lm_project_triage_company (id, project_id, source, status, company_name, added_by)
                    VALUES ('%s', '%s', 'MANUAL', 'IN_UNIVERSE', 'Gulf Industrial', '%s')"""
                    .formatted(FILED_COMPANY, PROJECT, USER));
            statement.execute("""
                    INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                    VALUES ('%s', '%s', 'Held Person', 'MANUAL', '%s')""".formatted(PERSON, WORKSPACE, USER));
            statement.execute("""
                    INSERT INTO app_lm_project_candidate (project_id, person_id, source, added_by)
                    VALUES ('%s', '%s', 'MANUAL', '%s')""".formatted(PROJECT, PERSON, USER));
        }
    }

    private static UUID workspaceOf(Connection connection, String table, UUID id) throws SQLException {
        return single(connection, "SELECT workspace_id FROM " + table + " WHERE id = ?", id);
    }

    private static UUID single(Connection connection, String sql, UUID parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getObject(1, UUID.class);
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
