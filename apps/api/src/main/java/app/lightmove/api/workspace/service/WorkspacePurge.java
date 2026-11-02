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
 * Deletes everything a deleted workspace held once its grace period is over (#926): every row of every table carrying
 * its id but the audit trail and billing records ({@link WorkspacePurgeTables}), then every file under {@code ws/{id}/},
 * leaving the workspace row a tombstone and a deletion certificate, audited and emailed to whoever deleted it.
 *
 * <p>A workspace is claimed on {@code app_lm_workspace}, which carries no policy, by a lease committed before any work,
 * so two instances never purge one and a purge whose instance died is resumed after the lease by the next. All the
 * work runs inside the workspace's {@code WorkspaceScope} and in batches of their own transactions; the certificate's
 * counts are taken at the first attempt and checked against an empty workspace at the last, so a purge that left a row
 * behind fails rather than certifying it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacePurge {

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
        for (WorkspacePurgeContributor contributor : contributors) {
            try {
                contributor.beforePurge(workspaceId);
            } catch (RuntimeException failed) {
                log.warn("{} could not release what deleted workspace {} held outside the database",
                        contributor.getClass().getSimpleName(), workspaceId, failed);
            }
        }

        List<String> order = tables.purgeOrder();
        WorkspacePurgeCertificate certificate = transaction.execute(status -> certificates.begin(workspaceId,
                purge.deletedAt(), purge.deletedBy(), purge.startedAt(), tables.rowsOf(workspaceId, order),
                tables.rowsOf(workspaceId, retainedCounted())));

        for (String table : order) {
            int deleted;
            do {
                deleted = transaction.execute(status -> tables.deleteBatch(table, workspaceId));
            } while (deleted > 0);
        }
        certificates.addDeletedFiles(certificate.id(), deleteFiles(workspaceId));

        Map<String, Long> left = tables.rowsOf(workspaceId, order).entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!left.isEmpty()) {
            throw new IllegalStateException("rows of deleted workspace " + workspaceId + " survived the purge: " + left);
        }

        PurgeNotice notice = transaction.execute(status -> {
            WorkspacePurgeCertificate completed = certificates.complete(workspaceId, now);
            PurgeNotice owed = tombstone(workspaceId, now, completed);
            audit.event(WorkspaceEventType.WORKSPACE_PURGED).workspace(workspaceId)
                    .target("workspace_purge_certificate", completed.id())
                    .detail("rows", completed.deletedRows())
                    .detail("files", completed.objectCount())
                    .detail("tables", completed.rowCounts().size())
                    .record();
            return owed;
        });
        log.info("Purged deleted workspace {}: {} rows, {} files; certificate {}", workspaceId,
                notice.certificate().deletedRows(), notice.certificate().objectCount(), notice.certificate().id());
        if (notice.email() != null) {
            emails.send(templates.buildWorkspacePurgedEmail(notice.email(), notice.workspaceName(),
                    DAY.format(now), notice.certificate().id().toString(), notice.certificate().deletedRows(),
                    notice.certificate().objectCount(), notice.certificate().bodySha256()));
        }
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

    /** Ids stay — the audit trail and billing rows reference them — and every word that named the firm goes. */
    private PurgeNotice tombstone(UUID workspaceId, Instant purgedAt, WorkspacePurgeCertificate certificate) {
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT name, purge_notice_email FROM app_lm_workspace WHERE id = ? FOR UPDATE", workspaceId);
        jdbc.update("""
                UPDATE app_lm_workspace
                   SET name = ?, slug = 'deleted-' || id::text, email_domain = 'deleted.invalid', logo_mark = NULL,
                       company_size = NULL, primary_region = NULL, team_focus = NULL, apollo_account_id = NULL,
                       company_industry = NULL, company_city = NULL, company_country = NULL, company_website = NULL,
                       company_linkedin_url = NULL, logo_url = NULL, persona = ?::jsonb, purge_notice_email = NULL,
                       purge_claimed_until = NULL, purged_at = ?, version = version + 1
                 WHERE id = ?""",
                TOMBSTONE_NAME, json.writeValueAsString(HiringPersona.empty()), Timestamp.from(purgedAt), workspaceId);
        return new PurgeNotice((String) before.get("purge_notice_email"), (String) before.get("name"), certificate);
    }

    private record ClaimedPurge(UUID workspaceId, Instant deletedAt, UUID deletedBy, Instant startedAt) {}

    private record PurgeNotice(String email, String workspaceName, WorkspacePurgeCertificate certificate) {}
}
