package app.lightmove.api.workspace.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.mfa.MfaPolicy;
import app.lightmove.api.core.security.mfa.MfaService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.dto.MemberTwoFactorResponse;
import app.lightmove.api.workspace.dto.WorkspaceTwoFactorResponse;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Security for an admin: who in the workspace has two-factor, and resetting it for a member who lost it.
 * A factor is the person's across every workspace, so a reset is offered only for someone who is in this one alone.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceTwoFactorService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspaceAccess access;
    private final UserRepository users;
    private final MfaPolicy mfaPolicy;
    private final MfaService mfaService;

    @Transactional(readOnly = true)
    public WorkspaceTwoFactorResponse overview(UUID callerId, UUID workspaceId) {
        Workspace workspace = requireWorkspace(workspaceId);
        List<WorkspaceMember> roster = members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE);
        List<UUID> userIds = roster.stream().map(WorkspaceMember::getUserId).toList();

        Map<UUID, User> byId = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Instant> enabledSince = mfaPolicy.enabledSince(userIds);
        Set<UUID> elsewhere = userIds.isEmpty() ? Set.of()
                : new HashSet<>(members.findUserIdsActiveElsewhere(userIds, workspaceId, MemberStatus.ACTIVE));

        List<MemberTwoFactorResponse> rows = roster.stream()
                .filter(member -> byId.containsKey(member.getUserId()))
                .map(member -> {
                    User user = byId.get(member.getUserId());
                    Instant enabledAt = enabledSince.get(user.getId());
                    return new MemberTwoFactorResponse(member.getId(), user.getId(), user.getFullName(),
                            user.getEmail(), user.getAvatarUrl(), rolesOf(member), enabledAt != null, enabledAt,
                            enabledAt != null && !user.getId().equals(callerId) && !elsewhere.contains(user.getId()));
                })
                .sorted(Comparator.comparing(MemberTwoFactorResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new WorkspaceTwoFactorResponse(mfaPolicy.isOffered(), workspace.isRequireMfa(), rows);
    }

    @Transactional
    public void reset(UUID actorId, UUID workspaceId, UUID memberId, HttpServletRequest request) {
        WorkspaceMember member = access.requireActiveMemberRow(memberId, workspaceId);
        if (member.getUserId().equals(actorId)) {
            throw ApiException.of(ErrorCode.MFA_RESET_SELF);
        }
        if (!members.findUserIdsActiveElsewhere(List.of(member.getUserId()), workspaceId, MemberStatus.ACTIVE)
                .isEmpty()) {
            throw ApiException.of(ErrorCode.MFA_RESET_OTHER_WORKSPACES);
        }
        mfaService.resetByAdmin(actorId, workspaceId, requireWorkspace(workspaceId).getName(), member.getUserId(),
                request);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private static List<WorkspaceRole> rolesOf(WorkspaceMember member) {
        return member.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .map(WorkspaceRole::valueOf)
                .toList();
    }
}
