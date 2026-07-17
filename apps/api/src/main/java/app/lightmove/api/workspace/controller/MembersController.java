package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.workspace.dto.WorkspaceDtos.ChangeRolesRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.MemberResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The roster. Membership is invitation-only, so there is nothing pending here — only active members,
 * their roles, and removal.
 *
 * <p>Every method takes the workspace from the authenticated principal, never from the path, and the
 * {@code @PreAuthorize} guards re-read the caller's membership from the database — a revoked admin's
 * still-valid token gets refused.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MembersController {

    private final MemberService memberService;
    private final WorkspaceAccess access;
    private final UserRepository users;

    /** The active roster, visible to any staff member. */
    @GetMapping
    @PreAuthorize("@workspaceAuth.staff(principal)")
    public ResponseEntity<List<MemberResponse>> list() {
        AuthPrincipal principal = CurrentUser.require();
        List<WorkspaceMember> roster = access.activeMembers(principal.requireWorkspaceId());

        Map<UUID, User> byId = users
                .findAllById(roster.stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return ResponseEntity.ok(roster.stream()
                .map(member -> toMemberResponse(member, byId.get(member.getUserId())))
                .toList());
    }

    @PatchMapping("/{memberId}")
    @PreAuthorize("@workspaceAuth.can(principal, 'MEMBER_MANAGE')")
    public ResponseEntity<MemberResponse> changeRoles(@PathVariable UUID memberId,
                                                      @Valid @RequestBody ChangeRolesRequest request,
                                                      HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        WorkspaceMember member = memberService.changeRoles(
                principal.userId(), principal.requireWorkspaceId(), memberId, request.roles(), httpRequest);

        return ResponseEntity.ok(toMemberResponse(member,
                users.findById(member.getUserId()).orElse(null)));
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("@workspaceAuth.can(principal, 'MEMBER_MANAGE')")
    public ResponseEntity<Void> remove(@PathVariable UUID memberId, HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        memberService.remove(principal.userId(), principal.requireWorkspaceId(), memberId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    private MemberResponse toMemberResponse(WorkspaceMember member, User user) {
        if (user == null) {
            throw new IllegalStateException(
                    "Membership " + member.getId() + " references a missing user");
        }
        return new MemberResponse(member.getId(), user.getId(), user.getFullName(), user.getEmail(),
                user.getTitle(), roleNames(member), member.getJoinedAt());
    }

    private static List<WorkspaceRole> roleNames(WorkspaceMember member) {
        return member.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .map(WorkspaceRole::valueOf)
                .toList();
    }
}
