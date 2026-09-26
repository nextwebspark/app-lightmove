package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.dto.ChangeRolesRequest;
import app.lightmove.api.workspace.dto.MemberResponse;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The roster of active members. The workspace is the principal's, never the path's, and the guards
 * re-read membership from the database, so a revoked admin's still-valid token is refused.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MembersController {

    private final MemberService memberService;
    private final WorkspaceAccess access;
    private final UserRepository users;

    /** Visible to any staff member. */
    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public List<MemberResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        List<WorkspaceMember> roster = access.activeStaff(principal.requireWorkspaceId());

        Map<UUID, User> byId = users
                .findAllById(roster.stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return roster.stream()
                .map(member -> toMemberResponse(member, byId.get(member.getUserId())))
                .toList();
    }

    @PatchMapping("/{memberId}")
    @RequireWorkspacePermission(WorkspaceAction.MEMBER_MANAGE)
    public MemberResponse changeRoles(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable UUID memberId,
                                      @Valid @RequestBody ChangeRolesRequest request,
                                      HttpServletRequest httpRequest) {
        WorkspaceMember member = memberService.changeRoles(
                principal.userId(), principal.requireWorkspaceId(), memberId, request.roles(), httpRequest);

        return toMemberResponse(member,
                users.findById(member.getUserId()).orElse(null));
    }

    @DeleteMapping("/{memberId}")
    @RequireWorkspacePermission(WorkspaceAction.MEMBER_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable UUID memberId, HttpServletRequest httpRequest) {
        memberService.remove(principal.userId(), principal.requireWorkspaceId(), memberId, httpRequest);
    }

    private MemberResponse toMemberResponse(WorkspaceMember member, User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Membership " + member.getId() + " references a missing user");
        }
        return new MemberResponse(member.getId(), user.getId(), user.getFullName(), user.getEmail(),
                user.getTitle(), user.getAvatarUrl(), roleNames(member), member.getJoinedAt());
    }

    private static List<WorkspaceRole> roleNames(WorkspaceMember member) {
        return member.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .map(WorkspaceRole::valueOf)
                .toList();
    }
}
