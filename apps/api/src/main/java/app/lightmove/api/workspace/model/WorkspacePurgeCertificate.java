package app.lightmove.api.workspace.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A workspace's deletion certificate, begun when its purge starts; the body and its hash once it has ended. */
public record WorkspacePurgeCertificate(UUID id, UUID workspaceId, Instant deletedAt, @Nullable UUID deletedBy,
                                        Instant purgeStartedAt, Map<String, Long> rowCounts,
                                        Map<String, Long> retainedCounts, long objectCount,
                                        @Nullable Instant purgedAt, @Nullable String body,
                                        @Nullable String bodySha256) {

    public boolean isComplete() {
        return purgedAt != null;
    }

    public long deletedRows() {
        return rowCounts.values().stream().mapToLong(Long::longValue).sum();
    }
}
