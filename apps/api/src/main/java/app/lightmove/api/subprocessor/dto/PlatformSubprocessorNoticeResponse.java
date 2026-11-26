package app.lightmove.api.subprocessor.dto;

import app.lightmove.api.core.tenant.SubprocessorNoticeReplies;
import app.lightmove.api.subprocessor.model.SubprocessorNotice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One notice as the console lists it: what was published, by whom, and what came back — the admins emailed, those
 * still owed an email, and how many workspaces objected. Counts, never which workspace.
 */
public record PlatformSubprocessorNoticeResponse(
        UUID id,
        String provider,
        String receives,
        String purpose,
        String region,
        LocalDate effectiveOn,
        String listUrl,
        boolean inEffect,
        Instant publishedAt,
        @Nullable String publishedBy,
        long emailed,
        long failed,
        long objections
) {

    public static PlatformSubprocessorNoticeResponse of(SubprocessorNotice notice, SubprocessorNoticeReplies replies,
                                                        LocalDate today) {
        return new PlatformSubprocessorNoticeResponse(notice.id(), notice.provider(), notice.receives(),
                notice.purpose(), notice.region(), notice.effectiveOn(), notice.listUrl(),
                notice.isInEffectOn(today), notice.publishedAt(), notice.publishedByName(), replies.emailed(),
                replies.failed(), replies.objections());
    }
}
