package app.lightmove.api.workspace.api;

import app.lightmove.api.auth.api.dto.AuthDtos.ApproveMemberRequest;
import app.lightmove.api.auth.api.dto.AuthDtos.PendingMemberResponse;
import app.lightmove.api.auth.infrastructure.UserRepository;
import app.lightmove.api.common.security.AuthPrincipal;
import app.lightmove.api.common.security.CurrentUser;
import app.lightmove.api.workspace.application.OnboardingService;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UserRepository users;

    /** People who have asked to join, waiting on a decision. */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingMemberResponse>> pending() {
        AuthPrincipal principal = CurrentUser.require();

        List<PendingMemberResponse> pending = onboarding
                .pendingRequests(principal.requireWorkspaceId())
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
