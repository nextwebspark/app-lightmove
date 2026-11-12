package app.lightmove.api.dataexport.dto;

import app.lightmove.api.dataexport.constant.WorkspaceExportStatus;
import app.lightmove.api.dataexport.model.WorkspaceExport;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The Data & retention page's export card. {@code status} is read at the moment of the request, so a file past its
 * expiry reads {@code EXPIRED} before the sweep has deleted it.
 */
public record WorkspaceExportResponse(UUID id, WorkspaceExportStatus status, String fileName,
                                      @Nullable Long sizeBytes, @Nullable Integer people,
                                      @Nullable Integer documents, @Nullable Integer positions, Instant requestedAt,
                                      @Nullable String requestedByName, @Nullable Instant completedAt,
                                      @Nullable Instant expiresAt) {

    public static WorkspaceExportResponse of(WorkspaceExport export, @Nullable String requestedByName, Instant now) {
        WorkspaceExportStatus status = export.getStatus() == WorkspaceExportStatus.READY
                && !export.isDownloadableAt(now) ? WorkspaceExportStatus.EXPIRED : export.getStatus();
        return new WorkspaceExportResponse(export.getId(), status, export.getFileName(), export.getSizeBytes(),
                export.getPeople(), export.getDocuments(), export.getPositions(), export.getRequestedAt(),
                requestedByName, export.getCompletedAt(), export.getExpiresAt());
    }
}
