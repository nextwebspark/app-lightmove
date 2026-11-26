package app.lightmove.api.subprocessor.dto;

import app.lightmove.api.subprocessor.model.SubprocessorNotice;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One notice as a workspace admin reads it, with this workspace's objection to it, if any. */
public record WorkspaceSubprocessorNoticeResponse(
        UUID id,
        String provider,
        String receives,
        String purpose,
        String region,
        LocalDate effectiveOn,
        String listUrl,
        boolean inEffect,
        @Nullable WorkspaceSubprocessorObjectionResponse objection
) {

    public static WorkspaceSubprocessorNoticeResponse of(SubprocessorNotice notice, LocalDate today,
                                                         @Nullable WorkspaceSubprocessorObjectionResponse objection) {
        return new WorkspaceSubprocessorNoticeResponse(notice.id(), notice.provider(), notice.receives(),
                notice.purpose(), notice.region(), notice.effectiveOn(), notice.listUrl(),
                notice.isInEffectOn(today), objection);
    }
}
