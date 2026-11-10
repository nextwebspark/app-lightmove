package app.lightmove.api.retention;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.retention.constant.RetentionTarget;
import app.lightmove.api.retention.service.RetentionSweeper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * #921: the retention sweep deletes what the schedule holds no longer, and nothing it still holds. The tenant tables
 * are under forced row-level security, so every fixture row is written and read through the superuser template, and
 * each due row is first shown to be there: a sweep that read nothing would otherwise pass for one that deleted it.
 */
@IntegrationTest
class RetentionSweeperIntegrationTest extends FlowTestSupport {

    private static final Instant NOW = Instant.now();

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired RetentionSweeper sweeper;

    @Test
    @DisplayName("expired tokens, challenges and stale vendor records go; a family with a live token stays whole")
    void globalRowsPastTheirPeriodGo() throws Exception {
        UUID user = UUID.fromString(db.queryForObject("select id::text from app_lm_user where email = ?", String.class,
                verifiedEmail("tokens")));
        UUID oldFamily = UUID.randomUUID();
        UUID oldNewest = refreshToken(user, oldFamily, null, daysAgo(40));
        refreshToken(user, oldFamily, oldNewest, daysAgo(45));
        UUID liveFamily = UUID.randomUUID();
        UUID liveNewest = refreshToken(user, liveFamily, null, NOW.plus(Duration.ofDays(5)));
        refreshToken(user, liveFamily, liveNewest, daysAgo(60));
        db.update("insert into app_lm_verification_token (user_id, token_hash, purpose, expires_at) values"
                + " (?, ?, 'PASSWORD_RESET', ?), (?, ?, 'PASSWORD_RESET', ?)",
                user, hash(), ts(daysAgo(31)), user, hash(), ts(daysAgo(1)));
        db.update("insert into app_lm_mfa_challenge (user_id, token_hash, expires_at) values (?, ?, ?), (?, ?, ?)",
                user, hash(), ts(daysAgo(2)), user, hash(), ts(NOW.minus(Duration.ofHours(1))));
        db.update("insert into app_lm_vendor_company (linkedin_slug, provider, fetched_at, found) values"
                + " (?, 'BRIGHTDATA', ?, false), (?, 'BRIGHTDATA', ?, false)",
                "stale-" + user, ts(daysAgo(31)), "fresh-" + user, ts(daysAgo(1)));
        assertThat(count("select count(*) from app_lm_refresh_token where family_id = ?", oldFamily)).isEqualTo(2);

        Map<RetentionTarget, Integer> deleted = sweeper.sweepAt(NOW);

        assertThat(deleted.get(RetentionTarget.REFRESH_TOKENS)).isGreaterThanOrEqualTo(2);
        assertThat(count("select count(*) from app_lm_refresh_token where family_id = ?", oldFamily)).isZero();
        assertThat(count("select count(*) from app_lm_refresh_token where family_id = ?", liveFamily)).isEqualTo(2);
        assertThat(count("select count(*) from app_lm_verification_token where user_id = ? and purpose = 'PASSWORD_RESET'",
                user)).isEqualTo(1);
        assertThat(count("select count(*) from app_lm_mfa_challenge where user_id = ?", user)).isEqualTo(1);
        assertThat(db.queryForList("select linkedin_slug from app_lm_vendor_company where linkedin_slug like ?",
                String.class, "%-" + user)).containsExactly("fresh-" + user);
    }

    @Test
    @DisplayName("each workspace's closed invitations, read notifications, digests, idle chats and old uses go; the rest stay")
    void tenantRowsPastTheirPeriodGoInTheirOwnWorkspace() throws Exception {
        UUID due = workspaceOf(verifiedEmail("due"), "Retention Due Firm");
        UUID user = UUID.fromString(db.queryForObject("select user_id::text from app_lm_workspace_member"
                + " where workspace_id = ? limit 1", String.class, due));
        UUID untouched = workspaceOf(verifiedEmail("fresh"), "Retention Fresh Firm");
        UUID freshUser = UUID.fromString(db.queryForObject("select user_id::text from app_lm_workspace_member"
                + " where workspace_id = ? limit 1", String.class, untouched));

        for (UUID[] workspace : new UUID[][] {{due, user}, {untouched, freshUser}}) {
            boolean old = workspace[0].equals(due);
            Instant when = old ? daysAgo(500) : daysAgo(1);
            UUID ws = workspace[0];
            UUID member = workspace[1];
            db.update("insert into app_lm_invitation (workspace_id, email, token_hash, invited_by, status, expires_at,"
                    + " role_id) values (?, ?, ?, ?, 'PENDING', ?, (select id from app_lm_role where scope = 'WORKSPACE'"
                    + " and name = 'MEMBER'))", ws, "invitee-" + ws + "@example.com", hash(), member, ts(when));
            db.update("insert into app_lm_notification (workspace_id, recipient_user_id, kind, group_key, read_at)"
                    + " values (?, ?, 'MENTIONED', ?, ?)", ws, member, "read-" + ws, ts(when));
            db.update("insert into app_lm_notification (workspace_id, recipient_user_id, kind, group_key, latest_at)"
                    + " values (?, ?, 'MENTIONED', ?, ?)", ws, member, "unread-" + ws, ts(daysAgo(500)));
            db.update("insert into app_lm_notification_digest (user_id, workspace_id, digest_date, sent_at)"
                    + " values (?, ?, ?, ?)", member, ws, LocalDate.of(2025, 1, 1), ts(when));
            UUID thread = UUID.randomUUID();
            db.update("insert into app_lm_assistant_thread (id, workspace_id, user_id, title, updated_at)"
                    + " values (?, ?, ?, 'Old chat', ?)", thread, ws, member, ts(when));
            db.update("insert into app_lm_assistant_turn (thread_id, question, answer, actor_user_id, workspace_id)"
                    + " values (?, 'Who?', 'Nobody.', ?, ?)", thread, member, ws);
            db.update("insert into app_lm_usage_event (workspace_id, user_id, kind, units, est_cost_fils, created_at)"
                    + " values (?, ?, 'ASSISTANT_ASK', 1, 0, ?)", ws, member, ts(when));
        }
        assertThat(count("select count(*) from app_lm_notification where workspace_id = ?", due)).isEqualTo(2);

        Map<RetentionTarget, Integer> deleted = sweeper.sweepAt(NOW);

        assertThat(deleted.get(RetentionTarget.INVITATIONS)).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from app_lm_invitation where workspace_id = ? and email like 'invitee-%'",
                due)).isZero();
        assertThat(db.queryForList("select group_key from app_lm_notification where workspace_id = ?", String.class,
                due)).containsExactly("unread-" + due);
        assertThat(count("select count(*) from app_lm_notification_digest where workspace_id = ?", due)).isZero();
        assertThat(count("select count(*) from app_lm_assistant_thread where workspace_id = ?", due)).isZero();
        assertThat(count("select count(*) from app_lm_assistant_turn where workspace_id = ?", due)).isZero();
        assertThat(count("select count(*) from app_lm_usage_event where workspace_id = ?", due)).isZero();

        assertThat(count("select count(*) from app_lm_invitation where workspace_id = ? and email like 'invitee-%'",
                untouched)).isEqualTo(1);
        assertThat(count("select count(*) from app_lm_notification where workspace_id = ?", untouched)).isEqualTo(2);
        assertThat(count("select count(*) from app_lm_notification_digest where workspace_id = ?", untouched))
                .isEqualTo(1);
        assertThat(count("select count(*) from app_lm_assistant_turn where workspace_id = ?", untouched)).isEqualTo(1);
        assertThat(count("select count(*) from app_lm_usage_event where workspace_id = ?", untouched)).isEqualTo(1);
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private String verifiedEmail(String who) throws Exception {
        String address = who + "-" + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;
        verifiedUser("Rita Retention", address);
        return address;
    }

    private UUID workspaceOf(String address, String name) throws Exception {
        createWorkspace(login(address), name);
        return UUID.fromString(db.queryForObject("select w.id::text from app_lm_workspace w join app_lm_workspace_member m"
                + " on m.workspace_id = w.id join app_lm_user u on u.id = m.user_id where u.email = ?", String.class,
                address));
    }

    private UUID refreshToken(UUID user, UUID family, UUID replacedBy, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        db.update("insert into app_lm_refresh_token (id, user_id, token_hash, family_id, expires_at, replaced_by_id)"
                + " values (?, ?, ?, ?, ?, ?)", id, user, hash(), family, ts(expiresAt), replacedBy);
        return id;
    }

    private long count(String sql, Object... arguments) {
        Long counted = db.queryForObject(sql, Long.class, arguments);
        return counted == null ? 0 : counted;
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
