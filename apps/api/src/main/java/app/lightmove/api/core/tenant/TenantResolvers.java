package app.lightmove.api.core.tenant;

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
 * credential's hash — asked before any workspace is known (V171). Each answers ids and the workspace, never the row:
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

    /** How many firms hold their own template under each code — the platform library's count, never which firms. */
    public Map<String, Long> firmTemplateCopiesByCode() {
        Map<String, Long> copies = new HashMap<>();
        jdbc.query("SELECT code, copies FROM app_lm_count_firm_templates_by_code()",
                (RowCallbackHandler) row -> copies.put(row.getString("code"), row.getLong("copies")));
        return copies;
    }

    private List<ResolvedRow> rows(String function, Object key) {
        return jdbc.query("SELECT id, workspace_id FROM " + function + "(?)", ROW, key);
    }

    private Optional<ResolvedRow> single(String function, Object key) {
        return rows(function, key).stream().findFirst();
    }
}
