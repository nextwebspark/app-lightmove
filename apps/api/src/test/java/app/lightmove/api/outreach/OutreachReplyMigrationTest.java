package app.lightmove.api.outreach;

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
 * V145: a line V144's deploy window left without its position is filed under its run's, every answered and bounced
 * run gets a placeholder line for what came back, and a direct Microsoft mailbox already grants a reply's words. Its
 * own container, stopped at V144 to seed, for {@code CompanyNotesBackfillMigrationTest}'s reason.
 */
class OutreachReplyMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000145a1");
    private static final UUID OTHER_USER = UUID.fromString("00000000-0000-0000-0000-0000000145a2");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000145b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000145c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000145d1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000145e1");
    private static final UUID SEQUENCE = UUID.fromString("00000000-0000-0000-0000-0000000145f1");
    private static final UUID REPLIED_RUN = UUID.fromString("00000000-0000-0000-0000-000000014501");
    private static final UUID BOUNCED_RUN = UUID.fromString("00000000-0000-0000-0000-000000014502");
    private static final UUID ACTIVE_RUN = UUID.fromString("00000000-0000-0000-0000-000000014503");
    private static final UUID UNFILED_MESSAGE = UUID.fromString("00000000-0000-0000-0000-000000014504");

    @Test
    @DisplayName("V145 files deploy-window lines, keeps what came back as placeholders, and records who grants the read")
    void keepsWhatCameBack() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "144");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "145");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT project_id || '|' || person_id FROM app_lm_outreach_message WHERE id = ?""",
                        UNFILED_MESSAGE)).containsExactly(PROJECT + "|" + PERSON);

                assertThat(strings(connection, """
                        SELECT kind || '|' || direction || '|' || status || '|' || coalesce(from_address, '-') || '|'
                               || coalesce(provider_message_id, '-') || '|' || thread_id || '|' || thread_gateway
                               || '|' || occurred_at::text
                        FROM app_lm_outreach_message WHERE enrollment_id = ? AND direction = 'INBOUND'""",
                        REPLIED_RUN)).containsExactly("REPLY|INBOUND|RECEIVED|priya@target.example|-|thread-1|DIRECT"
                        + "|2026-10-02 09:00:00+00");
                assertThat(strings(connection, """
                        SELECT kind || '|' || coalesce(from_address, '-') || '|' || occurred_at::text
                        FROM app_lm_outreach_message WHERE enrollment_id = ? AND direction = 'INBOUND'""",
                        BOUNCED_RUN)).containsExactly("BOUNCE|-|2026-10-03 09:00:00+00");
                assertThat(strings(connection, """
                        SELECT count(*)::text FROM app_lm_outreach_message
                        WHERE enrollment_id = ? AND direction = 'INBOUND'""", ACTIVE_RUN)).containsExactly("0");

                assertThat(strings(connection, """
                        SELECT provider || '|' || reply_text_granted FROM app_lm_mailbox_connection
                        WHERE workspace_id = ? ORDER BY provider""", WORKSPACE))
                        .containsExactly("google|false", "microsoft|true");

                // A line belongs to a position and a person from here on, and a reply is recorded once by its id.
                assertThatThrownBy(() -> execute(connection, """
                        INSERT INTO app_lm_outreach_message (workspace_id, sender_user_id, direction, kind, status)
                        VALUES ('%s', '%s', 'INBOUND', 'REPLY', 'RECEIVED')""".formatted(WORKSPACE, USER)))
                        .hasMessageContaining("project_id");
                String reply = """
                        INSERT INTO app_lm_outreach_message (workspace_id, sender_user_id, direction, kind, status,
                                                             project_id, person_id, provider_message_id)
                        VALUES ('%s', '%s', 'INBOUND', 'REPLY', 'RECEIVED', '%s', '%s', 'reply-1')"""
                        .formatted(WORKSPACE, USER, PROJECT, PERSON);
                execute(connection, reply);
                assertThatThrownBy(() -> execute(connection, reply))
                        .hasMessageContaining("app_lm_outreach_message_inbound_uk");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO app_lm_user (id, email, full_name) VALUES
                    ('%s', 'yara@firm.example', 'Yara Haddad'), ('%s', 'omar@firm.example', 'Omar Khalil')"""
                .formatted(USER, OTHER_USER));
        execute(connection, """
                INSERT INTO app_lm_workspace (id, name, slug, email_domain, created_by)
                VALUES ('%s', 'Seed Firm', 'seed-firm', 'firm.example', '%s')""".formatted(WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_client (id, workspace_id, name, created_by)
                VALUES ('%s', '%s', 'Group Finance', '%s')""".formatted(CLIENT, WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_project (id, workspace_id, client_id, position_title, created_by)
                VALUES ('%s', '%s', '%s', 'Chief Financial Officer', '%s')""".formatted(PROJECT, WORKSPACE, CLIENT, USER));
        execute(connection, """
                INSERT INTO app_lm_person (id, workspace_id, full_name, source, created_by)
                VALUES ('%s', '%s', 'Priya Raman', 'MANUAL', '%s')""".formatted(PERSON, WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                                                       daily_cap, connected_at, gateway, refresh_token_encrypted)
                VALUES ('%s', '%s', 'microsoft', 'yara@firm.example', 'direct:microsoft:seed', 'ACTIVE', 50,
                        timestamptz '2026-09-01 00:00:00+00', 'DIRECT', 'sealed')""".formatted(WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                                                       daily_cap, connected_at)
                VALUES ('%s', '%s', 'google', 'omar@firm.example', 'grant-seed', 'ACTIVE', 50,
                        timestamptz '2026-09-01 00:00:00+00')""".formatted(WORKSPACE, OTHER_USER));
        execute(connection, """
                INSERT INTO app_lm_outreach_sequence (id, workspace_id, project_id, name)
                VALUES ('%s', '%s', '%s', 'First approach')""".formatted(SEQUENCE, WORKSPACE, PROJECT));
        run(connection, REPLIED_RUN, "REPLIED", "replied_at", "2026-10-02 09:00:00+00");
        run(connection, BOUNCED_RUN, "BOUNCED", "stopped_at", "2026-10-03 09:00:00+00");
        run(connection, ACTIVE_RUN, "ACTIVE", "last_sent_at", "2026-10-04 09:00:00+00");
        // What the revision before V144 inserted while V144's deploy was rolling out: no position, no person.
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_outreach_message (id, workspace_id, enrollment_id, step, sender_user_id,
                                                     provider_message_id, thread_id, subject, body, sent_at)
                VALUES (?, ?, ?, 0, ?, 'msg-1', 'thread-3', 'Confidential', '<p>Hi</p>', now())""")) {
            statement.setObject(1, UNFILED_MESSAGE);
            statement.setObject(2, WORKSPACE);
            statement.setObject(3, ACTIVE_RUN);
            statement.setObject(4, USER);
            statement.execute();
        }
    }

    private static void run(Connection connection, UUID id, String status, String stampColumn, String stampedAt)
            throws SQLException {
        String thread = id.equals(REPLIED_RUN) ? "thread-1" : id.equals(BOUNCED_RUN) ? "thread-2" : "thread-3";
        execute(connection, """
                INSERT INTO app_lm_outreach_enrollment (id, workspace_id, project_id, sequence_id, person_id,
                                                        sender_user_id, to_address, status, first_subject,
                                                        first_body, enrolled_at, thread_id, thread_gateway, %s)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'priya@target.example', '%s', 'Confidential', 'Hi',
                        now(), '%s', 'DIRECT', timestamptz '%s')"""
                .formatted(stampColumn, id, WORKSPACE, PROJECT, SEQUENCE, PERSON, USER, status, thread, stampedAt));
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
