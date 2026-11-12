package app.lightmove.api.workspace.service;

import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.workspace.model.WorkspacePurgeCertificate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Purges a deleted workspace after its grace period (#926): every table but {@link WorkspacePurgeTables}' retained ones,
 * then files under {@code ws/{id}/}, leaving a tombstone and an emailed certificate. Claimed by a lease committed before
 * any work; a contributor still holding something fails the attempt, retried until {@link #RELEASE_PATIENCE}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacePurge {

    /** How long a purge waits on a provider before deleting the rows that were its only way back. */
    public static final Duration RELEASE_PATIENCE = Duration.ofDays(7);
    static final Duration NOTICE_PATIENCE = Duration.ofDays(7);
    private static final Duration LEASE = Duration.ofHours(1);
    private static final String TOMBSTONE_NAME = "Deleted workspace";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WorkspacePurgeTables tables;
    private final WorkspacePurgeCertificates certificates;
    private final List<WorkspacePurgeContributor> contributors;
    private final DocumentStore store;
    private final AuditService audit;
    private final EmailSender emails;
    private final EmailTemplates templates;
    private final ObjectMapper json;
    private final Clock clock;

    public static String storagePrefixOf(UUID workspaceId) {
        return "ws/" + workspaceId + "/";
    }

    @Scheduled(fixedDelayString = "${lightmove.retention.workspace-purge-interval}",
            initialDelayString = "${lightmove.retention.workspace-purge-interval}")
    public void scheduledPurge() {
        purgeDueAt(clock.instant());
    }

    /** Answers how many workspaces were purged. One that fails is left claimed until its lease runs out. */
    public int purgeDueAt(Instant now) {
        sendOwedNoticesAt(now);
        int purged = 0;
        Optional<ClaimedPurge> claimed;
        while ((claimed = claimNext(now)).isPresent()) {
            ClaimedPurge purge = claimed.get();
            try {
                WorkspaceScope.run(purge.workspaceId(), () -> purge(purge, now));
                purged++;
            } catch (RuntimeException failed) {
                log.error("Purging deleted workspace {} failed; it is tried again after {}", purge.workspaceId(),
                        LEASE, failed);
            }
        }
        return purged;
    }

    private Optional<ClaimedPurge> claimNext(Instant now) {
        List<ClaimedPurge> claimed = transaction.execute(status -> jdbc.query("""
                UPDATE app_lm_workspace w
                   SET purge_claimed_until = ?, purge_started_at = coalesce(w.purge_started_at, ?)
                 WHERE w.id = (SELECT id FROM app_lm_workspace
                                WHERE status = 'DELETED' AND purge_after <= ? AND purged_at IS NULL
                                  AND (purge_claimed_until IS NULL OR purge_claimed_until <= ?)
                                ORDER BY purge_after LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING w.id, w.deleted_at, w.deleted_by, w.purge_started_at""",
                (row, index) -> new ClaimedPurge(row.getObject("id", UUID.class),
                        row.getTimestamp("deleted_at").toInstant(), row.getObject("deleted_by", UUID.class),
                        row.getTimestamp("purge_started_at").toInstant()),
                Timestamp.from(now.plus(LEASE)), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)));
        return claimed == null ? Optional.empty() : claimed.stream().findFirst();
    }

    private void purge(ClaimedPurge purge, Instant now) {
        UUID workspaceId = purge.workspaceId();
        releaseHoldings(purge, now);
        renewLease(workspaceId);

        List<String> order = tables.purgeOrder();
        WorkspacePurgeCertificate begun = transaction.execute(status -> certificates.begin(workspaceId,
                purge.deletedAt(), purge.deletedBy(), purge.startedAt(), tables.rowsOf(workspaceId, order),
                tables.rowsOf(workspaceId, retainedCounted())));

        for (String table : order) {
            int deleted;
            do {
                deleted = transaction.execute(status -> {
                    renewLease(workspaceId);
                    return tables.deleteBatch(table, workspaceId);
                });
            } while (deleted > 0);
        }
        certificates.addDeletedFiles(begun.id(), deleteFiles(workspaceId));

        Map<String, Long> left = tables.rowsOf(workspaceId, order).entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!left.isEmpty()) {
            throw new IllegalStateException("rows of deleted workspace " + workspaceId + " survived the purge: " + left);
        }

        WorkspacePurgeCertificate certificate = transaction.execute(status -> {
            WorkspacePurgeCertificate completed = certificates.complete(workspaceId, now);
            tombstone(workspaceId, now);
            audit.event(WorkspaceEventType.WORKSPACE_PURGED).workspace(workspaceId)
                    .target("workspace_purge_certificate", completed.id())
                    .detail("rows", completed.deletedRows())
                    .detail("files", completed.objectCount())
                    .detail("tables", completed.rowCounts().size())
                    .record();
            return completed;
        });
        log.info("Purged deleted workspace {}: {} rows, {} files; certificate {}", workspaceId,
                certificate.deletedRows(), certificate.objectCount(), certificate.id());
        sendNotice(workspaceId, now);
    }

    /** Every contributor is asked on every attempt; any one still holding something holds the purge back. */
    private void releaseHoldings(ClaimedPurge purge, Instant now) {
        List<String> holding = new ArrayList<>();
        for (WorkspacePurgeContributor contributor : contributors) {
            try {
                contributor.beforePurge(purge.workspaceId());
            } catch (RuntimeException failed) {
                holding.add(contributor.getClass().getSimpleName());
                log.warn("{} could not yet release what deleted workspace {} held outside the database",
                        contributor.getClass().getSimpleName(), purge.workspaceId(), failed);
            }
        }
        if (holding.isEmpty()) {
            return;
        }
        if (now.isBefore(purge.startedAt().plus(RELEASE_PATIENCE))) {
            throw new IllegalStateException(holding + " still hold what deleted workspace " + purge.workspaceId()
                    + " kept outside the database");
        }
        log.error("{} still could not release what deleted workspace {} held outside the database {} after its purge"
                + " began; purging regardless", holding, purge.workspaceId(), RELEASE_PATIENCE);
    }

    /** Never shortens a lease: a pass run for an arranged moment claims ahead of the clock. */
    private void renewLease(UUID workspaceId) {
        jdbc.update("""
                UPDATE app_lm_workspace SET purge_claimed_until = greatest(purge_claimed_until, ?)
                 WHERE id = ? AND purged_at IS NULL""", Timestamp.from(clock.instant().plus(LEASE)), workspaceId);
    }

    private long deleteFiles(UUID workspaceId) {
        try {
            return store.deletePrefix(storagePrefixOf(workspaceId));
        } catch (IOException failed) {
            throw new UncheckedIOException("could not delete the files of deleted workspace " + workspaceId, failed);
        }
    }

    /** What the certificate counts as kept: the retained tables a workspace's rows are in, the certificate aside. */
    private static List<String> retainedCounted() {
        return WorkspacePurgeTables.RETAINED.stream()
                .filter(table -> !table.equals("app_lm_workspace_purge_certificate")
                        && !table.equals("app_lm_refresh_token"))
                .sorted()
                .toList();
    }

    /**
     * Ids stay — the audit trail and billing rows reference them — and every word that named the firm goes, but the
     * name the certificate's email gives, kept beside its address only until that email is accepted.
     */
    private void tombstone(UUID workspaceId, Instant purgedAt) {
        jdbc.update("""
                UPDATE app_lm_workspace
                   SET purge_notice_name = CASE WHEN purge_notice_email IS NULL THEN NULL ELSE name END,
                       name = ?, slug = 'deleted-' || id::text, email_domain = 'deleted.invalid', logo_mark = NULL,
                       company_size = NULL, primary_region = NULL, team_focus = NULL, apollo_account_id = NULL,
                       company_industry = NULL, company_city = NULL, company_country = NULL, company_website = NULL,
                       company_linkedin_url = NULL, logo_url = NULL, persona = ?::jsonb,
                       purge_claimed_until = NULL, purged_at = ?, version = version + 1
                 WHERE id = ?""",
                TOMBSTONE_NAME, json.writeValueAsString(HiringPersona.empty()), Timestamp.from(purgedAt), workspaceId);
    }

    private void sendOwedNoticesAt(Instant now) {
        List<UUID> owed = jdbc.queryForList("""
                SELECT id FROM app_lm_workspace
                 WHERE purged_at IS NOT NULL AND purge_notice_email IS NOT NULL
                   AND (purge_claimed_until IS NULL OR purge_claimed_until <= ?)""", UUID.class, Timestamp.from(now));
        for (UUID workspaceId : owed) {
            try {
                WorkspaceScope.run(workspaceId, () -> sendNotice(workspaceId, now));
            } catch (RuntimeException failed) {
                log.error("Sending the purge certificate of workspace {} failed; it is tried again after {}",
                        workspaceId, LEASE, failed);
            }
        }
    }

    /**
     * Claimed on the tombstone's lease, so of two instances one sends. The address and the name go once the mail
     * service accepts it, or once {@link #NOTICE_PATIENCE} has passed; a refused one waits out the lease.
     */
    private void sendNotice(UUID workspaceId, Instant now) {
        List<OwedNotice> claimed = transaction.execute(status -> jdbc.query("""
                UPDATE app_lm_workspace SET purge_claimed_until = ?
                 WHERE id = ? AND purged_at IS NOT NULL AND purge_notice_email IS NOT NULL
                   AND (purge_claimed_until IS NULL OR purge_claimed_until <= ?)
                RETURNING purge_notice_email, purge_notice_name, purged_at""",
                (row, index) -> new OwedNotice(row.getString("purge_notice_email"), row.getString("purge_notice_name"),
                        row.getTimestamp("purged_at").toInstant()),
                Timestamp.from(now.plus(LEASE)), workspaceId, Timestamp.from(now)));
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        OwedNotice notice = claimed.getFirst();
        Optional<WorkspacePurgeCertificate> certificate = transaction.execute(status -> certificates.of(workspaceId));
        boolean accepted = certificate != null && certificate.isPresent() && accepted(notice, certificate.get());
        if (!accepted && now.isBefore(notice.purgedAt().plus(NOTICE_PATIENCE))) {
            log.warn("The mail service did not accept the purge certificate of workspace {}; tried again after {}",
                    workspaceId, LEASE);
            return;
        }
        if (!accepted) {
            log.error("Gave up emailing the purge certificate of workspace {} after {}; a platform admin can read it",
                    workspaceId, NOTICE_PATIENCE);
        }
        jdbc.update("""
                UPDATE app_lm_workspace
                   SET purge_notice_email = NULL, purge_notice_name = NULL, purge_claimed_until = NULL
                 WHERE id = ?""", workspaceId);
    }

    private boolean accepted(OwedNotice notice, WorkspacePurgeCertificate certificate) {
        try {
            return emails.trySend(templates.buildWorkspacePurgedEmail(notice.email(), notice.workspaceName(),
                    DAY.format(notice.purgedAt()), certificate.id().toString(), certificate.deletedRows(),
                    certificate.objectCount(), certificate.bodySha256()));
        } catch (RuntimeException failed) {
            log.warn("Could not send the purge certificate of a deleted workspace", failed);
            return false;
        }
    }

    private record ClaimedPurge(UUID workspaceId, Instant deletedAt, UUID deletedBy, Instant startedAt) {}

    private record OwedNotice(String email, String workspaceName, Instant purgedAt) {}
}
