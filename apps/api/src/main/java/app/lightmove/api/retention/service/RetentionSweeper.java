package app.lightmove.api.retention.service;

import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RetentionSettings;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.retention.constant.RetentionTarget;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes the system rows {@code docs/privacy/retention-schedule.md} holds no longer (#921): expired sign-in tokens
 * and challenges, the vendor caches past their TTL, closed invitations, read notifications, sent digests, old
 * assistant chats and aged metered uses. Every delete is batched and harmless run twice, so instances need no claim.
 *
 * <p>The tenant tables are under forced row-level security: read with no workspace they answer nothing, and a sweep
 * would find nothing due. So the resolver names the workspaces holding a due row, and each is swept inside its own
 * {@link WorkspaceScope} — never every workspace, and never unscoped.
 */
@Slf4j
@Component
public class RetentionSweeper {

    private static final int BATCH = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantResolvers resolvers;
    private final CachedPeopleStore people;
    private final Clock clock;
    private final RetentionSettings retention;
    private final EnrichmentSettings enrichment;

    public RetentionSweeper(NamedParameterJdbcTemplate jdbc, TenantResolvers resolvers, CachedPeopleStore people,
                            Clock clock, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.resolvers = resolvers;
        this.people = people;
        this.clock = clock;
        this.retention = properties.retention();
        this.enrichment = properties.enrichment();
    }

    @Scheduled(fixedDelayString = "${lightmove.retention.sweep-interval}",
            initialDelayString = "${lightmove.retention.sweep-interval}")
    public void scheduledSweep() {
        sweepAt(clock.instant());
    }

    public Map<RetentionTarget, Integer> sweepAt(Instant now) {
        Map<RetentionTarget, Integer> deleted = new EnumMap<>(RetentionTarget.class);
        for (RetentionTarget target : RetentionTarget.values()) {
            deleted.put(target, 0);
        }
        deleted.put(RetentionTarget.REFRESH_TOKENS, refreshTokenFamilies(now.minus(retention.refreshTokensAfter())));
        deleted.put(RetentionTarget.VERIFICATION_TOKENS, inBatches("""
                DELETE FROM app_lm_verification_token WHERE id IN (
                    SELECT id FROM app_lm_verification_token WHERE expires_at < :cutoff LIMIT :batch)
                """, cutoff(now.minus(retention.verificationTokensAfter()))));
        deleted.put(RetentionTarget.MFA_CHALLENGES, inBatches("""
                DELETE FROM app_lm_mfa_challenge WHERE id IN (
                    SELECT id FROM app_lm_mfa_challenge WHERE expires_at < :cutoff LIMIT :batch)
                """, cutoff(now.minus(retention.mfaChallengesAfter()))));
        deleted.put(RetentionTarget.VENDOR_COMPANIES, inBatches("""
                DELETE FROM app_lm_vendor_company WHERE linkedin_slug IN (
                    SELECT linkedin_slug FROM app_lm_vendor_company WHERE fetched_at < :cutoff LIMIT :batch)
                """, cutoff(now.minus(enrichment.companyCacheTtl()))));
        deleted.put(RetentionTarget.VENDOR_PEOPLE, people.purgeFetchedBefore(now.minus(enrichment.peopleCacheTtl())));
        sweepTenantTables(now, deleted);
        if (deleted.values().stream().anyMatch(count -> count > 0)) {
            log.info("Retention sweep deleted {}", deleted);
        }
        return deleted;
    }

    private void sweepTenantTables(Instant now, Map<RetentionTarget, Integer> deleted) {
        Instant invitations = now.minus(retention.invitationsAfter());
        Instant notifications = now.minus(retention.notificationsAfter());
        Instant digests = now.minus(retention.digestsAfter());
        Instant threads = now.minus(retention.assistantThreadsAfter());
        Instant usage = now.minus(retention.usageEventsAfter());
        for (UUID workspaceId : resolvers.workspacesWithRetentionDue(invitations, notifications, digests, threads,
                usage)) {
            WorkspaceScope.run(workspaceId, () -> {
                add(deleted, RetentionTarget.INVITATIONS, inBatches("""
                        DELETE FROM app_lm_invitation WHERE id IN (
                            SELECT id FROM app_lm_invitation WHERE workspace_id = :workspaceId
                              AND ((status = 'ACCEPTED' AND accepted_at < :cutoff)
                                   OR (status <> 'ACCEPTED' AND expires_at < :cutoff))
                            LIMIT :batch)
                        """, cutoff(invitations).addValue("workspaceId", workspaceId)));
                add(deleted, RetentionTarget.NOTIFICATIONS, inBatches("""
                        DELETE FROM app_lm_notification WHERE id IN (
                            SELECT id FROM app_lm_notification WHERE workspace_id = :workspaceId
                              AND (read_at < :cutoff OR archived_at < :cutoff)
                            LIMIT :batch)
                        """, cutoff(notifications).addValue("workspaceId", workspaceId)));
                add(deleted, RetentionTarget.NOTIFICATION_DIGESTS, inBatches("""
                        DELETE FROM app_lm_notification_digest WHERE (user_id, workspace_id, digest_date) IN (
                            SELECT user_id, workspace_id, digest_date FROM app_lm_notification_digest
                            WHERE workspace_id = :workspaceId AND sent_at < :cutoff LIMIT :batch)
                        """, cutoff(digests).addValue("workspaceId", workspaceId)));
                add(deleted, RetentionTarget.ASSISTANT_THREADS, inBatches("""
                        DELETE FROM app_lm_assistant_thread WHERE id IN (
                            SELECT id FROM app_lm_assistant_thread WHERE workspace_id = :workspaceId
                              AND updated_at < :cutoff LIMIT :batch)
                        """, cutoff(threads).addValue("workspaceId", workspaceId)));
                add(deleted, RetentionTarget.USAGE_EVENTS, inBatches("""
                        DELETE FROM app_lm_usage_event WHERE id IN (
                            SELECT id FROM app_lm_usage_event WHERE workspace_id = :workspaceId
                              AND created_at < :cutoff LIMIT :batch)
                        """, cutoff(usage).addValue("workspaceId", workspaceId)));
            });
        }
    }

    /**
     * A family goes whole, once its newest token has been expired that long: each token names the one that replaced
     * it, and the family is how a reused token is recognised as stolen, so none of it may go while any of it lives.
     */
    private int refreshTokenFamilies(Instant cutoff) {
        return inBatches("""
                DELETE FROM app_lm_refresh_token WHERE family_id IN (
                    SELECT family_id FROM app_lm_refresh_token GROUP BY family_id
                    HAVING max(expires_at) < :cutoff LIMIT :batch)
                """, cutoff(cutoff));
    }

    private int inBatches(String sql, MapSqlParameterSource parameters) {
        parameters.addValue("batch", BATCH);
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.update(sql, parameters);
            total += deleted;
        } while (deleted > 0);
        return total;
    }

    private static MapSqlParameterSource cutoff(Instant cutoff) {
        return new MapSqlParameterSource("cutoff", Timestamp.from(cutoff));
    }

    private static void add(Map<RetentionTarget, Integer> deleted, RetentionTarget target, int count) {
        deleted.merge(target, count, Integer::sum);
    }
}
