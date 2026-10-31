package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.AuditActorKind;
import app.lightmove.api.workspace.constant.AuditLogGroup;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.dto.AuditLogEntryResponse;
import app.lightmove.api.workspace.model.AuditLine;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns audit rows into the log's lines: who acted, the phrase, the position. Names are read from live rows — the
 * roster, invitations, positions, clients — never from an event's metadata, which is not returned in any form.
 */
@Component
@RequiredArgsConstructor
class AuditEntryDescriber {

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

    private final AuditLogPhrasing phrasing;
    private final AuditTargetLabels targetLabels;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final InvitationRepository invitations;
    private final UserRepository users;

    List<AuditLogEntryResponse> describe(UUID workspaceId, List<AuditEvent> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        WorkspaceMode mode = workspaces.findById(workspaceId).map(Workspace::getMode).orElse(WorkspaceMode.COMPANY);

        Set<UUID> actorIds = rows.stream().map(AuditEvent::getActorUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> memberTargetIds = rows.stream().map(AuditEntryDescriber::memberTargetOf).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, WorkspaceMember> memberByUser = byKey(actorIds.isEmpty() ? List.of()
                : members.findByWorkspaceIdAndUserIdIn(workspaceId, actorIds), WorkspaceMember::getUserId);
        Map<UUID, WorkspaceMember> memberById = byKey(memberTargetIds.isEmpty() ? List.of()
                : members.findByWorkspaceIdAndIdIn(workspaceId, memberTargetIds), WorkspaceMember::getId);

        Set<UUID> userIds = new HashSet<>(actorIds);
        memberById.values().forEach(member -> userIds.add(member.getUserId()));
        Map<UUID, User> userById = byKey(users.findAllById(userIds), User::getId);

        Map<UUID, String> invitationEmails = nonNullValues(invitations.findAllById(idsOf(rows, INVITATION_TARGET))
                .stream()
                .filter(invitation -> workspaceId.equals(invitation.getWorkspaceId()))
                .toList(), Invitation::getId, Invitation::getEmail);
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
            String positionTitle = AuditService.PROJECT_TARGET.equals(row.getTargetType())
                    ? positionTitles.get(uuidOrNull(row.getTargetId())) : null;
            AuditLine line = new AuditLine(row.getEventType(), row.getOutcome(), row.getMetadata(), targetName, mode);
            return entryOf(row, phrasing.phrase(line), userById, memberByUser, positionTitle);
        }).toList();
    }

    /** Uncava's staff are named as Uncava with no address; a row with no actor is an API key's or the system's. */
    private static AuditLogEntryResponse entryOf(AuditEvent row, String summary, Map<UUID, User> userById,
                                                 Map<UUID, WorkspaceMember> memberByUser, String positionTitle) {
        String group = AuditLogGroup.of(row.getEventType()).map(AuditLogGroup::value).orElse(null);
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

    private static <T> Map<UUID, T> byKey(Collection<T> items, Function<T, UUID> key) {
        return nonNullValues(items, key, Function.identity());
    }

    /** {@code Collectors.toMap} refuses a null value; a row with nothing to name is simply left out. */
    private static <T, V> Map<UUID, V> nonNullValues(Collection<T> items, Function<T, UUID> key,
                                                     Function<T, V> value) {
        Map<UUID, V> map = new HashMap<>();
        for (T item : items) {
            V resolved = value.apply(item);
            if (resolved != null) {
                map.put(key.apply(item), resolved);
            }
        }
        return map;
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
}
