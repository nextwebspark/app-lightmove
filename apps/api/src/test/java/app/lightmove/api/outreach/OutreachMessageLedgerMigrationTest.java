package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.outreach.model.OutreachEmailBody;
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
 * V144: every email already sent becomes a line of its executive's conversation — filed under the run's position,
 * person and address and the sender's mailbox, its words its run's first email or decoded out of the HTML it went as. Its own container,
 * stopped at V143 to seed, for {@code CompanyNotesBackfillMigrationTest}'s reason.
 */
class OutreachMessageLedgerMigrationTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000144a1");
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000144b1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000144c1");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000144d1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000144e1");
    private static final UUID SEQUENCE = UUID.fromString("00000000-0000-0000-0000-0000000144f1");
    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000014401");
    private static final UUID MESSAGE = UUID.fromString("00000000-0000-0000-0000-000000014402");
    private static final UUID FOLLOW_UP = UUID.fromString("00000000-0000-0000-0000-000000014403");
    private static final UUID LATER_USER = UUID.fromString("00000000-0000-0000-0000-0000000144a2");
    private static final UUID LATER_RUN = UUID.fromString("00000000-0000-0000-0000-000000014404");
    private static final UUID LATER_MESSAGE = UUID.fromString("00000000-0000-0000-0000-000000014405");

    private static final String FIRST_BODY = "Hi Priya,\n\nYour treasury work stood out.";
    /** What a follow-up was written as: htmlOf turns every non-ASCII character here into a named reference. */
    private static final String FOLLOW_UP_TEXT = "José’s café – 5€ for Müller…\n\n"
            + "P&L <growth> at \"Al Naboodah\" isn't small.\nTalk soon: &amp;lt; stays as typed.";

    @Test
    @DisplayName("V144 files each sent email under its run's position and person, and reads its words back")
    void filesEachSentEmailAsAConversationLine() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            migrateTo(postgres, "143");
            try (Connection connection = connect(postgres)) {
                seed(connection);
            }

            migrateTo(postgres, "144");

            try (Connection connection = connect(postgres)) {
                assertThat(strings(connection, """
                        SELECT project_id || '|' || person_id || '|' || coalesce(candidate_id::text, '-') || '|'
                               || to_address || '|' || from_address || '|' || thread_gateway || '|' || direction
                               || '|' || channel || '|' || kind || '|' || status || '|' || body_format || '|'
                               || (occurred_at = sent_at)
                        FROM app_lm_outreach_message WHERE id = ?""", MESSAGE))
                        .containsExactly(PROJECT + "|" + PERSON + "|-|priya@target.example|yara@firm.example|DIRECT"
                                + "|OUTBOUND|EMAIL|SEQUENCE_STEP|SENT|TEXT|true");
                // A first email reads its run's frozen text; a follow-up is decoded out of the HTML it went as.
                assertThat(strings(connection, "SELECT body_text FROM app_lm_outreach_message WHERE id = ?", MESSAGE))
                        .containsExactly(FIRST_BODY);
                assertThat(OutreachEmailBody.htmlOf(FOLLOW_UP_TEXT)).contains("&eacute;", "&rsquo;", "&euro;");
                assertThat(strings(connection, "SELECT body_text FROM app_lm_outreach_message WHERE id = ?", FOLLOW_UP))
                        .containsExactly(FOLLOW_UP_TEXT);
                // The sender reconnected after this one went: their mailbox's address may not be the one it went from.
                assertThat(strings(connection, """
                        SELECT coalesce(from_address, '-') FROM app_lm_outreach_message WHERE id = ?""", LATER_MESSAGE))
                        .containsExactly("-");

                // A reply can never count against the daily cap, and a run's step is still sent once.
                assertThatThrownBy(() -> execute(connection, """
                        INSERT INTO app_lm_outreach_message (workspace_id, sender_user_id, direction, kind, status,
                                                             sent_at, project_id, person_id)
                        VALUES ('%s', '%s', 'INBOUND', 'REPLY', 'RECEIVED', now(), '%s', '%s')"""
                        .formatted(WORKSPACE, USER, PROJECT, PERSON)))
                        .hasMessageContaining("app_lm_outreach_message_inbound_unsent_chk");
                assertThatThrownBy(() -> execute(connection, """
                        INSERT INTO app_lm_outreach_message (workspace_id, enrollment_id, step, sender_user_id,
                                                             provider_message_id, subject, body, sent_at)
                        VALUES ('%s', '%s', 0, '%s', 'msg-again', 'Again', '<p>Again</p>', now())"""
                        .formatted(WORKSPACE, RUN, USER)))
                        .hasMessageContaining("app_lm_outreach_message_step_uk");
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'yara@firm.example', 'Yara Haddad')"""
                .formatted(USER));
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
                                                       daily_cap, connected_at)
                VALUES ('%s', '%s', 'google', 'yara@firm.example', 'grant-seed', 'ACTIVE', 50,
                        timestamptz '2026-09-01 00:00:00+00')""".formatted(WORKSPACE, USER));
        execute(connection, """
                INSERT INTO app_lm_outreach_sequence (id, workspace_id, project_id, name)
                VALUES ('%s', '%s', '%s', 'First approach')""".formatted(SEQUENCE, WORKSPACE, PROJECT));
        execute(connection, """
                INSERT INTO app_lm_outreach_enrollment (id, workspace_id, project_id, sequence_id, person_id,
                                                        sender_user_id, to_address, status, first_subject,
                                                        first_body, enrolled_at, thread_id, thread_gateway)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'priya@target.example', 'ACTIVE', 'Confidential',
                        E'%s', now(), 'thread-1', 'DIRECT')"""
                .formatted(RUN, WORKSPACE, PROJECT, SEQUENCE, PERSON, USER, FIRST_BODY.replace("\n", "\\n")));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_outreach_message (id, workspace_id, enrollment_id, step, sender_user_id,
                                                     provider_message_id, thread_id, subject, body, sent_at)
                VALUES (?, ?, ?, 0, ?, 'msg-1', 'thread-1', 'Confidential', ?,
                        timestamptz '2026-09-28 09:04:00+00')""")) {
            statement.setObject(1, MESSAGE);
            statement.setObject(2, WORKSPACE);
            statement.setObject(3, RUN);
            statement.setObject(4, USER);
            statement.setString(5, OutreachEmailBody.htmlOf(FIRST_BODY));
            statement.execute();
        }
        message(connection, FOLLOW_UP, RUN, 1, USER, "msg-2", "2026-09-30 09:04:00+00",
                OutreachEmailBody.htmlOf(FOLLOW_UP_TEXT));

        execute(connection, """
                INSERT INTO app_lm_user (id, email, full_name) VALUES ('%s', 'omar@firm.example', 'Omar Khalil')"""
                .formatted(LATER_USER));
        execute(connection, """
                INSERT INTO app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                                                       daily_cap, connected_at)
                VALUES ('%s', '%s', 'google', 'omar.new@firm.example', 'grant-later', 'ACTIVE', 50,
                        timestamptz '2026-10-15 00:00:00+00')""".formatted(WORKSPACE, LATER_USER));
        execute(connection, """
                INSERT INTO app_lm_outreach_enrollment (id, workspace_id, project_id, sequence_id, person_id,
                                                        sender_user_id, to_address, status, first_subject,
                                                        first_body, enrolled_at)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'priya@target.example', 'COMPLETED', 'Hello',
                        'Hi', now())""".formatted(LATER_RUN, WORKSPACE, PROJECT, SEQUENCE, PERSON, LATER_USER));
        message(connection, LATER_MESSAGE, LATER_RUN, 0, LATER_USER, "msg-3", "2026-09-28 09:04:00+00",
                OutreachEmailBody.htmlOf("Hi"));
    }

    private static void message(Connection connection, UUID id, UUID run, int step, UUID sender, String providerId,
                                String sentAt, String html) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_lm_outreach_message (id, workspace_id, enrollment_id, step, sender_user_id,
                                                     provider_message_id, thread_id, subject, body, sent_at)
                VALUES (?, ?, ?, ?, ?, ?, 'thread-1', 'Following up', ?, ?::timestamptz)""")) {
            statement.setObject(1, id);
            statement.setObject(2, WORKSPACE);
            statement.setObject(3, run);
            statement.setInt(4, step);
            statement.setObject(5, sender);
            statement.setString(6, providerId);
            statement.setString(7, html);
            statement.setString(8, sentAt);
            statement.execute();
        }
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
