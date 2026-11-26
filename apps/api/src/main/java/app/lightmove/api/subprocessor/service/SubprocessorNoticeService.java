package app.lightmove.api.subprocessor.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.tenant.SubprocessorNoticeReplies;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.subprocessor.dto.PlatformSubprocessorNoticeResponse;
import app.lightmove.api.subprocessor.dto.PlatformSubprocessorObjectionResponse;
import app.lightmove.api.subprocessor.dto.PublishSubprocessorNoticeRequest;
import app.lightmove.api.subprocessor.model.SubprocessorNotice;
import app.lightmove.api.subprocessor.model.SubprocessorNoticeDraft;
import app.lightmove.api.subprocessor.model.SubprocessorNoticePublished;
import app.lightmove.api.subprocessor.repository.SubprocessorNoticeRepository;
import app.lightmove.api.subprocessor.repository.SubprocessorObjectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The console's side of a sub-processor change: publish it at least 30 days ahead, which emails every workspace's
 * admins once committed, and read what came back. The objections are tenant rows, each workspace's read in its own
 * scope from the workspaces V248's resolver names.
 */
@Service
@RequiredArgsConstructor
public class SubprocessorNoticeService {

    public static final int NOTICE_PERIOD_DAYS = 30;

    private static final Pattern HTTPS_LINK = Pattern.compile("https://\\S+");

    private final SubprocessorNoticeRepository notices;
    private final SubprocessorObjectionRepository objections;
    private final SubprocessorNoticeAudience audience;
    private final TenantResolvers resolvers;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    public List<PlatformSubprocessorNoticeResponse> list() {
        LocalDate today = today();
        Map<UUID, SubprocessorNoticeReplies> replies = resolvers.subprocessorNoticeReplies();
        return notices.newestFirst().stream()
                .map(notice -> PlatformSubprocessorNoticeResponse.of(notice,
                        replies.getOrDefault(notice.id(), SubprocessorNoticeReplies.NONE), today))
                .toList();
    }

    public PlatformSubprocessorNoticeResponse publish(UUID userId, PublishSubprocessorNoticeRequest request,
                                                      HttpServletRequest httpRequest) {
        LocalDate today = today();
        SubprocessorNoticeDraft draft = draftOf(request, today);
        UUID noticeId = transactions.execute(status -> {
            UUID inserted = notices.insert(draft, userId);
            events.publishEvent(new SubprocessorNoticePublished(inserted));
            return inserted;
        });
        audit.event(PlatformEventType.SUBPROCESSOR_NOTICE_PUBLISHED)
                .actor(userId)
                .target("subprocessorNotice", noticeId)
                .detail("provider", draft.provider())
                .detail("effectiveOn", draft.effectiveOn().toString())
                .from(httpRequest)
                .record();
        return PlatformSubprocessorNoticeResponse.of(require(noticeId), SubprocessorNoticeReplies.NONE, today);
    }

    /** Every workspace's objection to the notice, the latest first. */
    public List<PlatformSubprocessorObjectionResponse> objectionsTo(UUID noticeId) {
        require(noticeId);
        return resolvers.workspacesWithSubprocessorObjections(noticeId).stream()
                .flatMap(workspaceId -> WorkspaceScope.call(workspaceId,
                        () -> objections.find(workspaceId, noticeId)).stream())
                .map(objection -> new PlatformSubprocessorObjectionResponse(objection.workspaceId(),
                        audience.workspaceName(objection.workspaceId()).orElse("A deleted workspace"),
                        objection.objectedByName(), objection.reason(), objection.objectedAt()))
                .sorted(Comparator.comparing(PlatformSubprocessorObjectionResponse::objectedAt).reversed())
                .toList();
    }

    private SubprocessorNoticeDraft draftOf(PublishSubprocessorNoticeRequest request, LocalDate today) {
        String listUrl = request.listUrl().strip();
        Map<String, String> refused = new LinkedHashMap<>();
        if (request.effectiveOn().isBefore(today.plusDays(NOTICE_PERIOD_DAYS))) {
            refused.put("effectiveOn", "Takes effect must be at least 30 days from today.");
        }
        if (!isHttpsLink(listUrl)) {
            refused.put("listUrl", "Enter a link that starts with https://");
        }
        if (!refused.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED, refused);
        }
        return new SubprocessorNoticeDraft(request.provider().strip(), request.receives().strip(),
                request.purpose().strip(), request.region().strip(), request.effectiveOn(), listUrl);
    }

    private static boolean isHttpsLink(String link) {
        if (!HTTPS_LINK.matcher(link).matches()) {
            return false;
        }
        try {
            String host = new URI(link).getHost();
            return host != null && !host.isBlank();
        } catch (URISyntaxException unparseable) {
            return false;
        }
    }

    private SubprocessorNotice require(UUID noticeId) {
        Optional<SubprocessorNotice> notice = notices.find(noticeId);
        return notice.orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
