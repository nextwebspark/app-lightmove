package app.lightmove.api.dataexport.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.export.service.ZipArchiveWriter;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.storage.service.StorageDeletionQueue;
import app.lightmove.api.dataexport.constant.WorkspaceExportStatus;
import app.lightmove.api.dataexport.dto.WorkspaceExportResponse;
import app.lightmove.api.dataexport.model.ExportedCounts;
import app.lightmove.api.dataexport.model.WorkspaceExport;
import app.lightmove.api.dataexport.model.WorkspaceExportDownload;
import app.lightmove.api.dataexport.model.WorkspaceExportRequested;
import app.lightmove.api.dataexport.repository.WorkspaceExportRepository;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Settings → Data & retention's export of the whole workspace (#926), an admin's: one at a time, built off the request
 * thread into the bucket ({@link WorkspaceArchiveWriter}), downloadable for {@code lightmove.retention.workspace-exports-
 * after} and then deleted. Starting one and every download are audited: the whole firm leaving as a file is not the
 * same act as reading a screen of it.
 */
@Slf4j
@Service
public class WorkspaceExportService {

    private static final String ZIP = "application/zip";
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EMAIL_DAY = DateTimeFormatter.ofPattern("d MMMM yyyy")
            .withZone(ZoneOffset.UTC);

    private final WorkspaceExportRepository exports;
    private final WorkspaceArchiveWriter archive;
    private final WorkspaceSettingsService workspaces;
    private final UserRepository users;
    private final DocumentStore store;
    private final StorageDeletionQueue deletions;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final EmailSender emails;
    private final EmailTemplates templates;
    private final TransactionTemplate ownTransaction;
    private final Duration lifetime;
    private final Duration deadline;
    private final String dataPageLink;
    private final Clock clock;

    public WorkspaceExportService(WorkspaceExportRepository exports, WorkspaceArchiveWriter archive,
                                  WorkspaceSettingsService workspaces, UserRepository users, DocumentStore store,
                                  StorageDeletionQueue deletions, AuditService audit, ApplicationEventPublisher events,
                                  EmailSender emails, EmailTemplates templates,
                                  PlatformTransactionManager transactionManager, LightMoveProperties properties,
                                  Clock clock) {
        this.exports = exports;
        this.archive = archive;
        this.workspaces = workspaces;
        this.users = users;
        this.store = store;
        this.deletions = deletions;
        this.audit = audit;
        this.events = events;
        this.emails = emails;
        this.templates = templates;
        // The worker runs after the request's commit, sometimes on its thread: a write must not join the finished one.
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lifetime = properties.retention().workspaceExportsAfter();
        this.deadline = properties.retention().workspaceExportDeadline();
        this.dataPageLink = properties.web().baseUrl() + "/settings/data";
        this.clock = clock;
    }

    public static String storageKeyOf(UUID workspaceId, UUID fileId) {
        return "ws/" + workspaceId + "/exports/" + fileId + ".zip";
    }

    @Transactional
    public WorkspaceExportResponse start(UUID userId, UUID workspaceId, HttpServletRequest request) {
        Instant now = clock.instant();
        exports.findByWorkspaceIdAndStatus(workspaceId, WorkspaceExportStatus.RUNNING).ifPresent(running -> {
            if (running.getRequestedAt().isAfter(now.minus(deadline))) {
                throw ApiException.of(ErrorCode.WORKSPACE_EXPORT_RUNNING);
            }
            closeStale(running, now);
        });
        Workspace workspace = workspaces.get(workspaceId).workspace();
        String fileName = ZipArchiveWriter.slugOf(workspace.getName(), "workspace") + "-export-"
                + FILE_DAY.format(now) + ".zip";
        WorkspaceExport export;
        try {
            export = exports.saveAndFlush(WorkspaceExport.start(workspaceId, userId, fileName,
                    storageKeyOf(workspaceId, UUID.randomUUID()), now));
        } catch (DataIntegrityViolationException racing) {
            // A second press landing beside the first: V202's partial unique index holds one build at a time.
            throw ApiException.of(ErrorCode.WORKSPACE_EXPORT_RUNNING);
        }
        audit.event(WorkspaceEventType.WORKSPACE_EXPORT_STARTED)
                .actor(userId).workspace(workspaceId).from(request)
                .target("workspace_export", export.getId())
                .record();
        events.publishEvent(new WorkspaceExportRequested(export.getId(), workspaceId));
        return responseOf(export, now);
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceExportResponse> latest(UUID workspaceId) {
        Instant now = clock.instant();
        return exports.findFirstByWorkspaceIdOrderByRequestedAtDesc(workspaceId).map(export -> responseOf(export, now));
    }

    @Transactional(readOnly = true)
    public WorkspaceExportDownload openDownload(UUID userId, UUID workspaceId, UUID exportId,
                                                HttpServletRequest request) {
        WorkspaceExport export = exports.findByIdAndWorkspaceId(exportId, workspaceId)
                .filter(found -> found.isDownloadableAt(clock.instant()))
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_EXPORT_UNAVAILABLE));
        audit.event(WorkspaceEventType.WORKSPACE_EXPORT_DOWNLOADED)
                .actor(userId).workspace(workspaceId).from(request)
                .target("workspace_export", export.getId())
                .detail("bytes", export.getSizeBytes())
                .record();
        return new WorkspaceExportDownload(export.getFileName(), export.getSizeBytes(), export.getStorageKey());
    }

    public void copy(WorkspaceExportDownload download, OutputStream out) throws IOException {
        try (var bytes = store.open(download.storageKey())) {
            bytes.transferTo(out);
        }
    }

    /**
     * The worker's body, in the workspace's scope. A build that fails deletes what it wrote and reads FAILED; the admin
     * starts another. Nothing is emailed once the workspace is deleted, since nobody can download it by then.
     */
    public void build(UUID exportId) {
        WorkspaceExport export = ownTransaction.execute(status -> exports.findById(exportId)
                .filter(WorkspaceExport::isRunning).orElse(null));
        if (export == null) {
            return;
        }
        Workspace workspace = workspaces.get(export.getWorkspaceId()).workspace();
        Instant startedAt = clock.instant();
        ExportedCounts counts;
        long size;
        ZipArchiveWriter zip = null;
        try {
            zip = new ZipArchiveWriter(store.create(export.getStorageKey(), ZIP));
            counts = archive.write(export.getWorkspaceId(), export.getRequestedBy(), workspace.getName(),
                    workspace.getMode(), startedAt, zip);
            zip.close();
            size = zip.bytesWritten();
        } catch (IOException | RuntimeException failed) {
            log.error("Exporting workspace {} failed", export.getWorkspaceId(), failed);
            discard(zip, export);
            ownTransaction.executeWithoutResult(status -> exports.findById(exportId)
                    .ifPresent(running -> running.fail(clock.instant())));
            return;
        }
        Instant builtAt = clock.instant();
        WorkspaceExport ready = ownTransaction.execute(status -> {
            WorkspaceExport running = exports.findById(exportId).orElseThrow();
            running.ready(size, counts, builtAt, builtAt.plus(lifetime));
            return running;
        });
        log.info("Exported workspace {}: {} bytes, {} people, {} documents, {} positions", export.getWorkspaceId(),
                size, counts.people(), counts.documents(), counts.positions());
        if (!workspace.isDeleted() && export.getRequestedBy() != null) {
            users.findById(export.getRequestedBy()).ifPresent(requester -> emails.send(
                    templates.buildWorkspaceExportReadyEmail(requester.getEmail(), requester.getFullName(),
                            workspace.getName(), EMAIL_DAY.format(Objects.requireNonNull(ready).getExpiresAt()),
                            dataPageLink)));
        }
    }

    /** {@link WorkspaceExportExpiry}'s, in the export's workspace: an expired file goes, a stale build fails. */
    @Transactional
    public void close(UUID exportId, Instant now) {
        exports.findById(exportId).ifPresent(export -> {
            if (export.isRunning()) {
                closeStale(export, now);
            } else if (export.getStatus() == WorkspaceExportStatus.READY && !export.isDownloadableAt(now)) {
                export.expire();
                deletions.enqueue(List.of(export.getStorageKey()));
            }
        });
    }

    /** Its worker died with its instance; whatever it had written is queued for deletion with it. */
    private void closeStale(WorkspaceExport running, Instant now) {
        running.fail(now);
        exports.flush();
        deletions.enqueue(List.of(running.getStorageKey()));
    }

    private void discard(ZipArchiveWriter zip, WorkspaceExport export) {
        try {
            if (zip != null) {
                zip.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // The file is deleted next whatever state the stream was left in.
        }
        try {
            store.delete(export.getStorageKey());
        } catch (IOException | RuntimeException failed) {
            ownTransaction.executeWithoutResult(status -> deletions.enqueue(List.of(export.getStorageKey())));
        }
    }

    private WorkspaceExportResponse responseOf(WorkspaceExport export, Instant now) {
        String requestedBy = export.getRequestedBy() == null ? null
                : users.findById(export.getRequestedBy()).map(User::getFullName).orElse(null);
        return WorkspaceExportResponse.of(export, requestedBy, now);
    }
}
