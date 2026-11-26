package app.lightmove.api.subprocessor.model;

import java.util.UUID;

/** A notice email claimed earlier and never recorded as sent: the retry pass's work. */
public record OwedSubprocessorNoticeEmail(UUID noticeId, UUID workspaceId, UUID userId, int attempts) {
}
