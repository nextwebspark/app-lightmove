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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes the system rows {@code docs/privacy/retention-schedule.md} holds no longer (#921): expired sign-in tokens
 * and challenges, the vendor caches past their TTL, closed invitations, read notifications, sent digests, old
 * assistant chats and aged metered uses. Every delete is batched and harmless run twice, so instances need no claim:
 * a batch skips the rows another instance has locked rather than waiting on them. Each table, in each workspace, is
 * swept on its own, so one that fails is logged and retried next hour without holding back the rest.
 * Made only where {@code lightmove.retention.enabled} is true ({@code RETENTION_ENABLED}, off by default).
 *
 * <p>The tenant tables are under forced row-level security: read with no workspace they answer nothing, and a sweep
 * would find nothing due. So the resolver names the workspaces holding a due row, and each is swept inside its own
 * {@link WorkspaceScope} — never every workspace, and never unscoped.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lightmove.retention", name = "enabled", havingValue = "true")
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
        sweep(deleted, RetentionTarget.REFRESH_TOKENS, null,
                () -> refreshTokenFamilies(now.minus(retention.refreshTokensAfter())));
        sweep(deleted, RetentionTarget.VERIFICATION_TOKENS, null, () -> inBatches("""
                DELETE FROM app_lm_verification_token WHERE id IN (
                    SELECT id FROM app_lm_verification_token WHERE expires_at < :cutoff
                    LIMIT :batch FOR UPDATE SKIP LOCKED)
                """, cutoff(now.minus(retention.verificationTokensAfter()))));
        sweep(deleted, RetentionTarget.MFA_CHALLENGES, null, () -> inBatches("""
                DELETE FROM app_lm_mfa_challenge WHERE id IN (
                    SELECT id FROM app_lm_mfa_challenge WHERE expires_at < :cutoff LIMIT :batch FOR UPDATE SKIP LOCKED)
                """, cutoff(now.minus(retention.mfaChallengesAfter()))));
        sweep(deleted, RetentionTarget.VENDOR_COMPANIES, null, () -> inBatches("""
                DELETE FROM app_lm_vendor_company WHERE linkedin_slug IN (
                    SELECT linkedin_slug FROM app_lm_vendor_company WHERE fetched_at < :cutoff
                    LIMIT :batch FOR UPDATE SKIP LOCKED)
                """, cutoff(now.minus(enrichment.companyCacheTtl()))));
        sweep(deleted, RetentionTarget.VENDOR_PEOPLE, null,
                () -> people.purgeFetchedBefore(now.minus(enrichment.peopleCacheTtl())));
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
        List<UUID> due;
        try {
            due = resolvers.workspacesWithRetentionDue(invitations, notifications, digests, threads, usage);
        } catch (RuntimeException failed) {
            log.warn("Retention sweep could not read which workspaces are due: {}", failed.toString());
            return;
        }
        for (UUID workspaceId : due) {
            WorkspaceScope.run(workspaceId, () -> {
                sweep(deleted, RetentionTarget.INVITATIONS, workspaceId, () -> inBatches("""
                        DELETE FROM app_lm_invitation WHERE id IN (
                            SELECT id FROM app_lm_invitation WHERE workspace_id = :workspaceId
                              AND ((status = 'ACCEPTED' AND accepted_at < :cutoff)
                                   OR (status <> 'ACCEPTED' AND expires_at < :cutoff))
                            LIMIT :batch FOR UPDATE SKIP LOCKED)
                        """, cutoff(invitations).addValue("workspaceId", workspaceId)));
                sweep(deleted, RetentionTarget.NOTIFICATIONS, workspaceId, () -> inBatches("""
                        DELETE FROM app_lm_notification WHERE id IN (
                            SELECT id FROM app_lm_notification WHERE workspace_id = :workspaceId
                              AND (read_at < :cutoff OR archived_at < :cutoff)
                            LIMIT :batch FOR UPDATE SKIP LOCKED)
                        """, cutoff(notifications).addValue("workspaceId", workspaceId)));
                sweep(deleted, RetentionTarget.NOTIFICATION_DIGESTS, workspaceId, () -> inBatches("""
                        DELETE FROM app_lm_notification_digest WHERE (user_id, workspace_id, digest_date) IN (
                            SELECT user_id, workspace_id, digest_date FROM app_lm_notification_digest
                            WHERE workspace_id = :workspaceId AND sent_at < :cutoff
                            LIMIT :batch FOR UPDATE SKIP LOCKED)
                        """, cutoff(digests).addValue("workspaceId", workspaceId)));
                sweep(deleted, RetentionTarget.ASSISTANT_THREADS, workspaceId, () -> inBatches("""
                        DELETE FROM app_lm_assistant_thread WHERE id IN (
                            SELECT id FROM app_lm_assistant_thread WHERE workspace_id = :workspaceId
                              AND updated_at < :cutoff LIMIT :batch FOR UPDATE SKIP LOCKED)
                        """, cutoff(threads).addValue("workspaceId", workspaceId)));
                sweep(deleted, RetentionTarget.USAGE_EVENTS, workspaceId, () -> inBatches("""
                        DELETE FROM app_lm_usage_event WHERE id IN (
                            SELECT id FROM app_lm_usage_event WHERE workspace_id = :workspaceId
                              AND created_at < :cutoff LIMIT :batch FOR UPDATE SKIP LOCKED)
                        """, cutoff(usage).addValue("workspaceId", workspaceId)));
            });
        }
    }

    /** Every statement runs on its own connection, so a failed one leaves nothing aborted for the next. */
    private static void sweep(Map<RetentionTarget, Integer> deleted, RetentionTarget target,
                              @Nullable UUID workspaceId, IntSupplier work) {
        try {
            deleted.merge(target, work.getAsInt(), Integer::sum);
        } catch (RuntimeException failed) {
            if (workspaceId == null) {
                log.warn("Retention sweep of {} failed: {}", target, failed.toString());
            } else {
                log.warn("Retention sweep of {} in workspace {} failed: {}", target, workspaceId, failed.toString());
            }
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
}
