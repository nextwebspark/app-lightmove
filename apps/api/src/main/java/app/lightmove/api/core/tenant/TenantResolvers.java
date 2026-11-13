package app.lightmove.api.core.tenant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Which workspace holds a key a door arrives with — a webhook's grant, calendar or bot, a public page's slug, a
 * credential's hash — asked before any workspace is known (V189). Each answers ids and the workspace, never the row:
 * the caller opens {@link WorkspaceScope} on what it is told and reads the row there, under the fence. Resolve, then
 * scope; never widen a scope to make an empty read work.
 */
@Component
@RequiredArgsConstructor
public class TenantResolvers {

    private static final RowMapper<ResolvedRow> ROW = (rs, rowNum) ->
            new ResolvedRow(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class));

    private final JdbcTemplate jdbc;

    public List<ResolvedRow> mailboxesByGrant(String grantId) {
        return rows("app_lm_resolve_mailboxes_by_grant", grantId);
    }

    public List<ResolvedRow> mailboxesByBookingConfiguration(String configurationId) {
        return rows("app_lm_resolve_mailboxes_by_booking_configuration", configurationId);
    }

    public List<ResolvedRow> mailboxesByRecallCalendar(String calendarId) {
        return rows("app_lm_resolve_mailboxes_by_recall_calendar", calendarId);
    }

    /** A picture an email shows, by its public token; a photo answers a null workspace. */
    public Optional<ResolvedRow> emailImage(String token) {
        return single("app_lm_resolve_email_image", token);
    }

    /** A privacy notice's public page, by the hash of its link's token (V218). */
    public Optional<ResolvedRow> personPrivacyLink(String tokenHash) {
        return single("app_lm_resolve_person_privacy_link", tokenHash);
    }

    public Optional<ResolvedRow> mailboxByBookingSlug(String slug) {
        return single("app_lm_resolve_mailbox_by_booking_slug", slug);
    }

    public List<ResolvedRow> mailboxesOfUser(UUID userId) {
        return rows("app_lm_resolve_mailboxes_of_user", userId);
    }

    public Optional<ResolvedRow> mailboxAuthorization(String stateHash) {
        return single("app_lm_resolve_mailbox_authorization", stateHash);
    }

    public Optional<ResolvedRow> recordingByBot(String recallBotId) {
        return single("app_lm_resolve_recording_by_bot", recallBotId);
    }

    public Optional<ResolvedRow> recordingByFinalTranscript(String transcriptId) {
        return single("app_lm_resolve_recording_by_final_transcript", transcriptId);
    }

    public Optional<ResolvedRow> apiKey(String tokenHash) {
        return single("app_lm_resolve_api_key", tokenHash);
    }

    /** By any of a grant's live hashes, or a refresh token it rotated away. */
    public List<ResolvedRow> oauthAuthorizationsByHash(String tokenHash) {
        return rows("app_lm_resolve_oauth_authorization", tokenHash);
    }

    public Optional<ResolvedRow> oauthAuthorization(UUID id) {
        return single("app_lm_resolve_oauth_authorization_by_id", id);
    }

    public List<ResolvedRow> oauthAuthorizationsOfUser(UUID userId) {
        return rows("app_lm_resolve_oauth_authorizations_of_user", userId);
    }

    public Optional<ResolvedRow> invitation(String tokenHash) {
        return single("app_lm_resolve_invitation", tokenHash);
    }

    public List<ResolvedRow> pendingInvitationsFor(String email) {
        return rows("app_lm_resolve_pending_invitations_for", email);
    }

    /** The workspace a Stripe customer bills; {@code id} is that workspace too. */
    public Optional<ResolvedRow> billingCustomer(String stripeCustomerId) {
        return single("app_lm_resolve_billing_customer", stripeCustomerId);
    }

    public boolean hasStartedATrial(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT app_lm_trial_started_by(?)", Boolean.class, userId));
    }

    /**
     * The workspaces holding a row the retention sweep is due to delete — each cutoff the oldest a row of its table may
     * be — so the sweep visits those alone rather than every workspace. Ids only.
     */
    public List<UUID> workspacesWithRetentionDue(Instant invitationsBefore, Instant notificationsBefore,
                                                 Instant digestsBefore, Instant threadsBefore, Instant usageBefore) {
        return jdbc.queryForList("SELECT workspace_id FROM app_lm_resolve_workspaces_with_retention_due(?, ?, ?, ?, ?)",
                UUID.class, Timestamp.from(invitationsBefore), Timestamp.from(notificationsBefore),
                Timestamp.from(digestsBefore), Timestamp.from(threadsBefore), Timestamp.from(usageBefore));
    }

    /** Whole-workspace exports past their expiry, or still building past the deadline a live worker would have met. */
    public List<ResolvedRow> workspaceExportsToClose(Instant now, Instant runningBefore) {
        return jdbc.query("SELECT id, workspace_id FROM app_lm_resolve_workspace_exports_to_close(?, ?)", ROW,
                Timestamp.from(now), Timestamp.from(runningBefore));
    }

    /** How many firms hold their own template under each code — the platform library's count, never which firms. */
    public Map<String, Long> firmTemplateCopiesByCode() {
        Map<String, Long> copies = new HashMap<>();
        jdbc.query("SELECT code, copies FROM app_lm_count_firm_templates_by_code()",
                (RowCallbackHandler) row -> copies.put(row.getString("code"), row.getLong("copies")));
        return copies;
    }

    /** A run's or a consultant's own email due at {@code at}, or a claim still held: the dispatcher's work (V191). */
    public List<UUID> workspacesWithSendsDue(Instant at) {
        return workspaces("SELECT * FROM app_lm_workspaces_with_sends_due(?)", Timestamp.from(at));
    }

    /** An open task due within {@code (after, until]} not yet announced as both due soon and overdue. */
    public List<UUID> workspacesWithTasksFallingDue(Instant after, Instant until) {
        return workspaces("SELECT * FROM app_lm_workspaces_with_tasks_falling_due(?, ?)",
                Timestamp.from(after), Timestamp.from(until));
    }

    public List<UUID> workspacesWithNotificationEmailsDue(Instant at) {
        return workspaces("SELECT * FROM app_lm_workspaces_with_notification_emails_due(?)", Timestamp.from(at));
    }

    public List<UUID> workspacesWithCopilotListening() {
        return workspaces("SELECT * FROM app_lm_workspaces_with_copilot_listening()");
    }

    public List<UUID> workspacesOwingSeatSync(Instant before, int limit) {
        return workspaces("SELECT * FROM app_lm_workspaces_owing_seat_sync(?, ?)", Timestamp.from(before), limit);
    }

    /** An unsettled hold past its time, or a grant past its expiry still holding credits. */
    public List<UUID> workspacesWithLapsedCredits(Instant at, int limit) {
        return workspaces("SELECT * FROM app_lm_workspaces_with_lapsed_credits(?, ?)", Timestamp.from(at), limit);
    }

    private List<UUID> workspaces(String sql, Object... args) {
        return jdbc.queryForList(sql, UUID.class, args);
    }

    private List<ResolvedRow> rows(String function, Object key) {
        return jdbc.query("SELECT id, workspace_id FROM " + function + "(?)", ROW, key);
    }

    private Optional<ResolvedRow> single(String function, Object key) {
        return rows(function, key).stream().findFirst();
    }
}
