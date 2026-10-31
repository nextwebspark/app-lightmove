package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.model.AuditLogQuery;
import app.lightmove.api.core.audit.repository.AuditEventRepository;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.AuditLogGroup;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.dto.AuditLogEntryResponse;
import app.lightmove.api.workspace.dto.AuditLogPageResponse;
import app.lightmove.api.workspace.dto.AuditLogPersonResponse;
import app.lightmove.api.workspace.model.AuditLogFilters;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settings → Security → Audit log: the workspace's trail read through {@link AuditLogGroup}, a page or a CSV at a time. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository events;
    private final AuditEntryDescriber describer;
    private final AuditService audit;
    private final AuditLogCsvWriter csvWriter;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final LightMoveProperties properties;

    @Transactional(readOnly = true)
    public AuditLogPageResponse page(UUID workspaceId, AuditLogFilters filters, Long before, Integer limit) {
        int size = Math.clamp(limit == null ? DEFAULT_PAGE_SIZE : limit, 1, MAX_PAGE_SIZE);
        List<AuditEvent> rows = events.findWorkspaceLog(queryOf(workspaceId, filters, before), size + 1);
        boolean hasMore = rows.size() > size;
        List<AuditEvent> shown = hasMore ? rows.subList(0, size) : rows;
        return new AuditLogPageResponse(describer.describe(workspaceId, shown),
                hasMore ? shown.getLast().getId() : null);
    }

    /** The same view as a CSV, refused whole past the ceiling rather than cut short, and itself an audit line. */
    @Transactional(readOnly = true)
    public String export(UUID userId, UUID workspaceId, AuditLogFilters filters, HttpServletRequest request) {
        int cap = properties.export().maxAuditEvents();
        AuditLogQuery query = queryOf(workspaceId, filters, null);
        if (events.countWorkspaceLog(query, cap + 1) > cap) {
            // The ceiling is the server's own number, so it may travel in a user-facing message.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This export covers more than " + cap + " events, past the limit. Narrow the dates or the "
                            + "category and export again.");
        }
        List<AuditLogEntryResponse> entries = describer.describe(workspaceId, events.findWorkspaceLog(query, cap));
        String csv = csvWriter.write(entries);

        audit.event(WorkspaceEventType.AUDIT_LOG_EXPORTED).actor(userId).workspace(workspaceId).from(request)
                .detail("rows", entries.size())
                .detail("group", filters.group() == null ? "all" : filters.group().value())
                .detail("outcome", filters.outcome() == null ? "all" : filters.outcome().name())
                .detail("actorFiltered", filters.actor() != null)
                .detailIfPresent("from", filters.from() == null ? null : filters.from().toString())
                .detailIfPresent("to", filters.to() == null ? null : filters.to().toString())
                .record();
        return csv;
    }

    /** Everyone the workspace ever held, for the Person filter: removed members still answer for what they did. */
    @Transactional(readOnly = true)
    public List<AuditLogPersonResponse> people(UUID workspaceId) {
        Map<UUID, WorkspaceMember> memberByUser = members.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(WorkspaceMember::getUserId, Function.identity()));
        return users.findAllById(memberByUser.keySet()).stream()
                .map(user -> new AuditLogPersonResponse(user.getId(), user.getFullName(), user.getAvatarUrl(),
                        memberByUser.get(user.getId()).getStatus() != MemberStatus.ACTIVE))
                .sorted(Comparator.comparing(AuditLogPersonResponse::removed)
                        .thenComparing(person -> String.valueOf(person.name()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Members' account events are read only for All and Sign-in: no other Category lists them. */
    private static AuditLogQuery queryOf(UUID workspaceId, AuditLogFilters filters, Long before) {
        AuditLogGroup group = filters.group();
        return AuditLogQuery.of(workspaceId, before, filters.from(), filters.to(), filters.actor(),
                filters.outcome() == null ? null : filters.outcome().name(),
                group == null ? null : group.codes(), AuditLogGroup.HIDDEN_CODES,
                group == null || group == AuditLogGroup.SIGN_IN, AuditLogGroup.ACCOUNT_SECURITY_CODES);
    }
}
