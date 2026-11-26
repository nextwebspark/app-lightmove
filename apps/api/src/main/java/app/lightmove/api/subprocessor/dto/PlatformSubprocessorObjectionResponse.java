package app.lightmove.api.subprocessor.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One workspace's objection, as the console reads it: the firm, the admin who last wrote it, and why. */
public record PlatformSubprocessorObjectionResponse(
        UUID workspaceId,
        String workspaceName,
        @Nullable String objectedBy,
        String reason,
        Instant objectedAt
) {
}
