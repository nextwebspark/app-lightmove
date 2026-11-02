package app.lightmove.api.core.security.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V170 clears every provider link stored before it, with its owner, so the next sign-in's provider copies one in. */
class UserAvatarMigrationTest {

    @Test
    @DisplayName("V170 clears stored provider links and their source; a person without one is untouched")
    void clearsProviderLinks() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "169");
            UUID linked = UUID.randomUUID();
            UUID legacy = UUID.randomUUID();
            UUID bare = UUID.randomUUID();
            try (Connection connection = connect(postgres)) {
                user(connection, linked, "https://media.licdn.com/expired.jpg", "LINKEDIN");
                user(connection, legacy, "https://lh3.googleusercontent.com/a/old.jpg", null);
                user(connection, bare, null, null);
            }

            migrateTo(postgres, "170");

            try (Connection connection = connect(postgres)) {
                for (UUID id : new UUID[] {linked, legacy, bare}) {
                    try (PreparedStatement select = connection.prepareStatement(
                            "SELECT avatar_url, avatar_source FROM app_lm_user WHERE id = ?")) {
                        select.setObject(1, id);
                        try (ResultSet row = select.executeQuery()) {
                            assertThat(row.next()).isTrue();
                            assertThat(row.getString("avatar_url")).isNull();
                            assertThat(row.getString("avatar_source")).isNull();
                        }
                    }
                }
            }
        }
    }

    private static void user(Connection connection, UUID id, String avatarUrl, String source) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO app_lm_user (id, email, full_name, avatar_url, avatar_source)
                VALUES (?, ?, 'Alok Kumar', ?, ?)""")) {
            insert.setObject(1, id);
            insert.setString(2, id + "@firm.example");
            insert.setString(3, avatarUrl);
            insert.setString(4, source);
            insert.executeUpdate();
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
