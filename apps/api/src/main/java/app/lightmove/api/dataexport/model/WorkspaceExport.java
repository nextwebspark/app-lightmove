package app.lightmove.api.dataexport.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.dataexport.constant.WorkspaceExportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One export of a whole workspace (V179): built off the request, downloadable until it expires. */
@Entity
@Table(name = "app_lm_workspace_export")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceExport extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceExportStatus status;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "people")
    private Integer people;

    @Column(name = "documents")
    private Integer documents;

    @Column(name = "positions")
    private Integer positions;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public static WorkspaceExport start(UUID workspaceId, UUID requestedBy, String fileName, String storageKey,
                                        Instant now) {
        WorkspaceExport export = new WorkspaceExport();
        export.workspaceId = workspaceId;
        export.requestedBy = requestedBy;
        export.status = WorkspaceExportStatus.RUNNING;
        export.fileName = fileName;
        export.storageKey = storageKey;
        export.requestedAt = now;
        return export;
    }

    public void ready(long sizeBytes, ExportedCounts counts, Instant now, Instant expiresAt) {
        this.status = WorkspaceExportStatus.READY;
        this.sizeBytes = sizeBytes;
        this.people = counts.people();
        this.documents = counts.documents();
        this.positions = counts.positions();
        this.completedAt = now;
        this.expiresAt = expiresAt;
    }

    public void fail(Instant now) {
        this.status = WorkspaceExportStatus.FAILED;
        this.completedAt = now;
    }

    public void expire() {
        this.status = WorkspaceExportStatus.EXPIRED;
    }

    public boolean isRunning() {
        return status == WorkspaceExportStatus.RUNNING;
    }

    public boolean isDownloadableAt(Instant now) {
        return status == WorkspaceExportStatus.READY && now.isBefore(expiresAt);
    }
}
