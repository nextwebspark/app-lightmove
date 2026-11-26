package app.lightmove.api.subprocessor.model;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One workspace's objection to one notice, as last written. {@code objectedByName} is null once erased. */
public record SubprocessorObjection(
        UUID noticeId,
        UUID workspaceId,
        @Nullable String objectedByName,
        String reason,
        Instant objectedAt
) {
}
