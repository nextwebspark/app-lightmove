package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

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

/** V245: a standing objection gets the door it came through; a colleague's flag in the same words stays theirs. */
class PersonObjectionSourceMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000245a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000245b1");
    private static final UUID OBJECTED_ON_PAGE = UUID.fromString("00000000-0000-0000-0000-0000000245c1");
    private static final UUID OPTED_OUT = UUID.fromString("00000000-0000-0000-0000-0000000245c2");
    private static final UUID TYPED_BY_A_COLLEAGUE = UUID.fromString("00000000-0000-0000-0000-0000000245c3");
    private static final UUID TEAMS_OWN_FLAG = UUID.fromString("00000000-0000-0000-0000-0000000245c4");
    private static final UUID CONTACTABLE = UUID.fromString("00000000-0000-0000-0000-0000000245c5");

    @Test
    @DisplayName("V245 reads the person's own doors off the reasons they wrote with nobody as the setter")
    void backfillsTheObjectionSource() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "244");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "245");

            try (Connection connection = connect(postgres)) {
                assertThat(sourceOf(connection, OBJECTED_ON_PAGE)).isEqualTo("OBJECTED_ON_PRIVACY_PAGE");
                assertThat(sourceOf(connection, OPTED_OUT)).isEqualTo("OPTED_OUT_BY_EMAIL");
                assertThat(sourceOf(connection, TYPED_BY_A_COLLEAGUE)).as("a colleague's words are no objection")
                        .isNull();
                assertThat(sourceOf(connection, TEAMS_OWN_FLAG)).isNull();
                assertThat(sourceOf(connection, CONTACTABLE)).isNull();
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'objection@example.com', 'Seed User')"""
                    .formatted(USER));
            statement.execute("""
                    INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                    VALUES ('%s', 'Objection Firm', 'objection-firm', 'example.com', '%s')"""
                    .formatted(WORKSPACE, USER));
        }
        person(connection, OBJECTED_ON_PAGE, "Objected through their privacy page", null);
        person(connection, OPTED_OUT, "Opted out by email", null);
        person(connection, TYPED_BY_A_COLLEAGUE, "Opted out by email", USER);
        person(connection, TEAMS_OWN_FLAG, "Works at the client", USER);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES (?, ?, 'Contactable Person', 'MANUAL', ?)""")) {
            statement.setObject(1, CONTACTABLE);
            statement.setObject(2, WORKSPACE);
            statement.setObject(3, USER);
            statement.executeUpdate();
        }
    }

    private static void person(Connection connection, UUID id, String reason, UUID setBy) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by, do_not_contact,
                                           do_not_contact_reason, do_not_contact_set_by, do_not_contact_set_at)
                VALUES (?, ?, ?, 'MANUAL', ?, true, ?, ?, now())""")) {
            statement.setObject(1, id);
            statement.setObject(2, WORKSPACE);
            statement.setString(3, "Person " + id);
            statement.setObject(4, USER);
            statement.setString(5, reason);
            statement.setObject(6, setBy);
            statement.executeUpdate();
        }
    }

    private static String sourceOf(Connection connection, UUID person) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT do_not_contact_source FROM app_lm_person WHERE id = ?")) {
            statement.setObject(1, person);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
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
