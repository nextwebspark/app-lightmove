package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.repository.AuditEventRepository;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.AuditActorKind;
import app.lightmove.api.workspace.constant.AuditLogGroup;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.dto.AuditLogEntryResponse;
import app.lightmove.api.workspace.dto.AuditLogPageResponse;
import app.lightmove.api.workspace.dto.AuditLogPersonResponse;
import app.lightmove.api.workspace.model.AuditLine;
import app.lightmove.api.workspace.model.AuditLogFilters;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Security → Audit log: the workspace's own trail, read through {@link AuditLogGroup}'s types and phrased
 * by {@link AuditLogPhrasing}. Names come from live rows — the roster, invitations, positions, clients — and never
 * from an event's metadata, which is not returned in any form.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    private static final UUID NOBODY = new UUID(0, 0);
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    /** Stands in for an open filter's list: an empty {@code IN ()} is a syntax error. */
    private static final List<String> NO_TYPES = List.of("-");
    private static final String MEMBER_TARGET = "member";
    private static final String INVITATION_TARGET = "invitation";
    private static final String CLIENT_TARGET = "client";
    /** The one metadata key read for anything but phrasing: the membership a team change names, resolved by id. */
    private static final String TEAM_MEMBER_KEY = "memberId";

    private static final Set<String> PLATFORM_CODES = Arrays.stream(PlatformEventType.values())
            .map(PlatformEventType::code)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> KEY_CODES = Set.of(
            ProjectEventType.PUBLIC_API_READ.code(), ProjectEventType.MCP_TOOL_CALL.code());

    private final AuditEventRepository events;
    private final AuditService audit;
    private final AuditLogPhrasing phrasing;
    private final AuditLogCsvWriter csvWriter;
    private final AuditTargetLabels targetLabels;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final LightMoveProperties properties;

    @Transactional(readOnly = true)
    public AuditLogPageResponse page(UUID workspaceId, AuditLogFilters filters, Long before, Integer limit) {
        int size = Math.clamp(limit == null ? DEFAULT_PAGE_SIZE : limit, 1, MAX_PAGE_SIZE);
        List<AuditEvent> rows = read(workspaceId, filters, before, size + 1);
        boolean hasMore = rows.size() > size;
        List<AuditEvent> shown = hasMore ? rows.subList(0, size) : rows;
        return new AuditLogPageResponse(describe(workspaceId, shown), hasMore ? shown.getLast().getId() : null);
    }

    /** The same view as a CSV, refused whole past the ceiling, and itself an audit line. */
    @Transactional(readOnly = true)
    public String export(UUID userId, UUID workspaceId, AuditLogFilters filters, HttpServletRequest request) {
        int cap = properties.export().maxAuditEvents();
        long total = count(workspaceId, filters, cap + 1);
        if (total > cap) {
            // The ceiling is the server's own number, so it may travel in a user-facing message.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This export covers more than " + cap + " events, past the limit. Narrow the dates or the "
                            + "category and export again.");
        }
        List<AuditLogEntryResponse> entries = describe(workspaceId, read(workspaceId, filters, null, cap));
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

    /** Everyone the workspace ever held, for the Person filter — removed members still answer for what they did. */
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

    private List<AuditEvent> read(UUID workspaceId, AuditLogFilters filters, Long before, int limit) {
        QueryShape shape = QueryShape.of(filters);
        return events.findWorkspaceLog(workspaceId, before == null ? Long.MAX_VALUE : before,
                filters.from() == null ? Instant.EPOCH : filters.from(),
                filters.to() == null ? FAR_FUTURE : filters.to(),
                filters.actor() == null, filters.actor() == null ? NOBODY : filters.actor(),
                filters.outcome() == null, filters.outcome() == null ? "-" : filters.outcome().name(),
                shape.anyType(), shape.types(), AuditLogGroup.HIDDEN_CODES,
                shape.withAccount(), AuditLogGroup.ACCOUNT_SECURITY_CODES, limit);
    }

    private long count(UUID workspaceId, AuditLogFilters filters, int limit) {
        QueryShape shape = QueryShape.of(filters);
        return events.countWorkspaceLog(workspaceId, Long.MAX_VALUE,
                filters.from() == null ? Instant.EPOCH : filters.from(),
                filters.to() == null ? FAR_FUTURE : filters.to(),
                filters.actor() == null, filters.actor() == null ? NOBODY : filters.actor(),
                filters.outcome() == null, filters.outcome() == null ? "-" : filters.outcome().name(),
                shape.anyType(), shape.types(), AuditLogGroup.HIDDEN_CODES,
                shape.withAccount(), AuditLogGroup.ACCOUNT_SECURITY_CODES, limit);
    }

    private List<AuditLogEntryResponse> describe(UUID workspaceId, List<AuditEvent> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        WorkspaceMode mode = workspaces.findById(workspaceId).map(Workspace::getMode).orElse(WorkspaceMode.COMPANY);
        List<WorkspaceMember> roster = members.findByWorkspaceId(workspaceId);
        Map<UUID, WorkspaceMember> memberById = roster.stream()
                .collect(Collectors.toMap(WorkspaceMember::getId, Function.identity()));
        Map<UUID, WorkspaceMember> memberByUser = roster.stream()
                .collect(Collectors.toMap(WorkspaceMember::getUserId, Function.identity()));

        Set<UUID> userIds = new HashSet<>();
        rows.stream().map(AuditEvent::getActorUserId).filter(Objects::nonNull).forEach(userIds::add);
        rows.stream().map(AuditLogService::memberTargetOf).filter(Objects::nonNull)
                .map(memberById::get).filter(Objects::nonNull).map(WorkspaceMember::getUserId).forEach(userIds::add);
        Map<UUID, User> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, String> invitationEmails = invitations.findAllById(idsOf(rows, INVITATION_TARGET)).stream()
                .filter(invitation -> workspaceId.equals(invitation.getWorkspaceId()))
                .collect(Collectors.toMap(Invitation::getId, Invitation::getEmail));
        Map<UUID, String> clientNames = targetLabels.clientNames(workspaceId, idsOf(rows, CLIENT_TARGET));
        Map<UUID, String> positionTitles = targetLabels.positionTitles(workspaceId,
                idsOf(rows, AuditService.PROJECT_TARGET));

        return rows.stream().map(row -> {
            String targetName = switch (String.valueOf(row.getTargetType())) {
                case INVITATION_TARGET -> invitationEmails.get(uuidOrNull(row.getTargetId()));
                case CLIENT_TARGET -> clientNames.get(uuidOrNull(row.getTargetId()));
                default -> {
                    WorkspaceMember member = memberById.get(memberTargetOf(row));
                    User named = member == null ? null : userById.get(member.getUserId());
                    yield named == null ? null : named.getFullName();
                }
            };
            UUID positionId = AuditService.PROJECT_TARGET.equals(row.getTargetType())
                    ? uuidOrNull(row.getTargetId()) : null;
            AuditLine line = new AuditLine(row.getEventType(), row.getOutcome(), row.getMetadata(), targetName, mode);
            return entryOf(row, line, userById, memberByUser, positionId == null ? null : positionTitles.get(positionId));
        }).toList();
    }

    private AuditLogEntryResponse entryOf(AuditEvent row, AuditLine line, Map<UUID, User> userById,
                                          Map<UUID, WorkspaceMember> memberByUser, String positionTitle) {
        String group = AuditLogGroup.of(row.getEventType()).map(AuditLogGroup::value).orElse(null);
        String summary = phrasing.phrase(line);
        String outcome = row.getOutcome().name();
        if (PLATFORM_CODES.contains(row.getEventType())) {
            return new AuditLogEntryResponse(row.getId(), row.getOccurredAt(), row.getEventType(), group, outcome,
                    AuditActorKind.UNCAVA, null, "Uncava", null, false, summary, positionTitle, null);
        }
        if (row.getActorUserId() == null) {
            AuditActorKind kind = KEY_CODES.contains(row.getEventType()) ? AuditActorKind.API_KEY : AuditActorKind.SYSTEM;
            return new AuditLogEntryResponse(row.getId(), row.getOccurredAt(), row.getEventType(), group, outcome,
                    kind, null, kind == AuditActorKind.API_KEY ? "Workspace API key" : "System", null, false,
                    summary, positionTitle, row.getIpAddress());
        }
        User actor = userById.get(row.getActorUserId());
        WorkspaceMember membership = memberByUser.get(row.getActorUserId());
        return new AuditLogEntryResponse(row.getId(), row.getOccurredAt(), row.getEventType(), group, outcome,
                AuditActorKind.MEMBER, row.getActorUserId(), actor == null ? null : actor.getFullName(),
                actor == null ? null : actor.getAvatarUrl(),
                membership == null || membership.getStatus() != MemberStatus.ACTIVE,
                summary, positionTitle, row.getIpAddress());
    }

    /** The membership a row is about: its target, or the one a team change names in its metadata. */
    private static UUID memberTargetOf(AuditEvent row) {
        if (MEMBER_TARGET.equals(row.getTargetType())) {
            return uuidOrNull(row.getTargetId());
        }
        if (ProjectEventType.PROJECT_TEAM_CHANGED.code().equals(row.getEventType())) {
            Object memberId = row.getMetadata().get(TEAM_MEMBER_KEY);
            return memberId == null ? null : uuidOrNull(memberId.toString());
        }
        return null;
    }

    private static Set<UUID> idsOf(List<AuditEvent> rows, String targetType) {
        return rows.stream()
                .filter(row -> targetType.equals(row.getTargetType()))
                .map(row -> uuidOrNull(row.getTargetId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static UUID uuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    /** How a filter row becomes the query's flags: the account branch is read only for All and Sign-in. */
    private record QueryShape(boolean anyType, List<String> types, boolean withAccount) {

        static QueryShape of(AuditLogFilters filters) {
            AuditLogGroup group = filters.group();
            if (group == null) {
                return new QueryShape(true, NO_TYPES, true);
            }
            return new QueryShape(false, List.copyOf(group.codes()), group == AuditLogGroup.SIGN_IN);
        }
    }
}
