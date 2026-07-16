package app.lightmove.api.workspace.controller;

import app.lightmove.api.workspace.dto.WorkspaceDtos.ApproveMemberRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.ChangeRoleRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.MemberResponse;
import app.lightmove.api.workspace.dto.WorkspaceDtos.PendingMemberResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.workspace.service.MemberService;
import app.lightmove.api.workspace.service.OnboardingService;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Membership decisions — who gets into the workspace.
 *
 * <p>Every method takes the workspace from the authenticated principal, never from the path. That is
 * the difference between an admin managing their own firm and an admin of any firm managing yours.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MembersController {

    private final OnboardingService onboarding;
    private final MemberService memberService;
    private final UserRepository users;

    /** The active roster, visible to any member. */
    @GetMapping
    public ResponseEntity<List<MemberResponse>> list() {
        AuthPrincipal principal = CurrentUser.require();
        List<WorkspaceMember> roster = memberService.activeMembers(
                principal.userId(), principal.requireWorkspaceId());

        Map<UUID, User> byId = users
                .findAllById(roster.stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return ResponseEntity.ok(roster.stream()
                .map(member -> toMemberResponse(member, byId.get(member.getUserId())))
                .toList());
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> changeRole(@PathVariable UUID memberId,
                                                     @Valid @RequestBody ChangeRoleRequest request,
                                                     HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        WorkspaceMember member = memberService.changeRole(
                principal.userId(), principal.requireWorkspaceId(), memberId, request.role(), httpRequest);

        return ResponseEntity.ok(toMemberResponse(member,
                users.findById(member.getUserId()).orElse(null)));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable UUID memberId, HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        memberService.remove(principal.userId(), principal.requireWorkspaceId(), memberId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    /** People who have asked to join, waiting on a decision. */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingMemberResponse>> pending() {
        AuthPrincipal principal = CurrentUser.require();

        List<PendingMemberResponse> pending = onboarding
                .pendingRequests(principal.userId(), principal.requireWorkspaceId())
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(pending);
    }

    /**
     * Let someone in.
     *
     * <p>This is the moment a person gains access to a firm's candidate pipeline, so the service
     * re-reads the caller's admin role from the database rather than trusting the JWT claim, which may
     * be up to fifteen minutes stale.
     */
    @PostMapping("/{memberId}/approve")
    public ResponseEntity<PendingMemberResponse> approve(@PathVariable UUID memberId,
                                                         @Valid @RequestBody ApproveMemberRequest request,
                                                         HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();

        WorkspaceMember member = onboarding.approve(
                principal.userId(), principal.requireWorkspaceId(), memberId, request.role(), httpRequest);

        return ResponseEntity.ok(toResponse(member));
    }

    @PostMapping("/{memberId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID memberId, HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();

        onboarding.reject(principal.userId(), principal.requireWorkspaceId(), memberId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    private MemberResponse toMemberResponse(WorkspaceMember member, User user) {
        if (user == null) {
            throw new IllegalStateException(
                    "Membership " + member.getId() + " references a missing user");
        }
        return new MemberResponse(member.getId(), user.getId(), user.getFullName(), user.getEmail(),
                user.getTitle(), member.getRole(), member.getJoinedAt());
    }

    private PendingMemberResponse toResponse(WorkspaceMember member) {
        return users.findById(member.getUserId())
                .map(user -> new PendingMemberResponse(
                        member.getId(),
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        member.getRole(),
                        member.getCreatedAt()))
                .orElseThrow(() -> new IllegalStateException(
                        "Membership " + member.getId() + " references a missing user"));
    }
}
