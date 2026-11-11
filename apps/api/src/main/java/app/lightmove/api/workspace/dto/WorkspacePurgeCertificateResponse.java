package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.WorkspacePurgeCertificate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A deletion certificate as a platform admin reads it: {@code body} is the exact text {@code sha256} was taken over. */
public record WorkspacePurgeCertificateResponse(UUID id, UUID workspaceId, Instant deletedAt, Instant purgeStartedAt,
                                                Instant purgedAt, Map<String, Long> deletedRows, long deletedFiles,
                                                Map<String, Long> retainedRows, String body, String sha256) {

    public static WorkspacePurgeCertificateResponse of(WorkspacePurgeCertificate certificate) {
        return new WorkspacePurgeCertificateResponse(certificate.id(), certificate.workspaceId(),
                certificate.deletedAt(), certificate.purgeStartedAt(), certificate.purgedAt(), certificate.rowCounts(),
                certificate.objectCount(), certificate.retainedCounts(), certificate.body(), certificate.bodySha256());
    }
}
