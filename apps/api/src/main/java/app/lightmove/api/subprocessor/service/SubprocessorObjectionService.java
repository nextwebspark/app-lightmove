package app.lightmove.api.subprocessor.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.subprocessor.dto.ObjectToSubprocessorRequest;
import app.lightmove.api.subprocessor.dto.WorkspaceSubprocessorNoticeResponse;
import app.lightmove.api.subprocessor.dto.WorkspaceSubprocessorObjectionResponse;
import app.lightmove.api.subprocessor.model.SubprocessorNotice;
import app.lightmove.api.subprocessor.model.SubprocessorObjection;
import app.lightmove.api.subprocessor.repository.SubprocessorNoticeRepository;
import app.lightmove.api.subprocessor.repository.SubprocessorObjectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A workspace admin's side: every change Uncava announced, with this workspace's objection to each, and the objection
 * itself — written, or changed, until the change takes effect (UTC), then refused.
 */
@Service
@RequiredArgsConstructor
public class SubprocessorObjectionService {

    private final SubprocessorNoticeRepository notices;
    private final SubprocessorObjectionRepository objections;
    private final AuditService audit;
    private final Clock clock;

    public List<WorkspaceSubprocessorNoticeResponse> list(UUID workspaceId) {
        LocalDate today = today();
        Map<UUID, SubprocessorObjection> held = objections.ofWorkspace(workspaceId).stream()
                .collect(Collectors.toMap(SubprocessorObjection::noticeId, Function.identity()));
        return notices.newestFirst().stream()
                .map(notice -> responseOf(notice, held.get(notice.id()), today))
                .toList();
    }

    public WorkspaceSubprocessorNoticeResponse object(UUID userId, UUID workspaceId, UUID noticeId,
                                                      ObjectToSubprocessorRequest request,
                                                      HttpServletRequest httpRequest) {
        SubprocessorNotice notice = notices.find(noticeId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        LocalDate today = today();
        if (notice.isInEffectOn(today)) {
            throw ApiException.of(ErrorCode.SUBPROCESSOR_NOTICE_CLOSED);
        }
        String reason = request.reason().strip();
        boolean objectedBefore = objections.save(workspaceId, noticeId, userId, reason);
        audit.event(WorkspaceEventType.SUBPROCESSOR_OBJECTION_RECORDED)
                .actor(userId)
                .workspace(workspaceId)
                .target("subprocessorNotice", noticeId)
                .detail("action", objectedBefore ? "changed" : "objected")
                .detail("provider", notice.provider())
                .from(httpRequest)
                .record();
        return responseOf(notice, objections.find(workspaceId, noticeId).orElse(null), today);
    }

    private static WorkspaceSubprocessorNoticeResponse responseOf(SubprocessorNotice notice,
                                                                  SubprocessorObjection objection, LocalDate today) {
        return WorkspaceSubprocessorNoticeResponse.of(notice, today,
                objection == null ? null : WorkspaceSubprocessorObjectionResponse.of(objection));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
