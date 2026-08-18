package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.dto.InvitationResponse;
import app.lightmove.api.workspace.dto.InviteRequest;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.InviteCommand;
import app.lightmove.api.workspace.service.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outstanding invitations, managed from Settings → Members. Gated on the MEMBER_INVITE action here;
 * the service keeps its own imperative checks too, because it is also reached from the anonymous
 * {@code /onboarding/accept-invitation-signup} endpoint, outside any request's SecurityContext.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationsController {

    private final InvitationService invitations;
    private final UserRepository users;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'MEMBER_INVITE')")
    public ResponseEntity<List<InvitationResponse>> pending(@AuthenticationPrincipal AuthPrincipal principal) {
        List<Invitation> pending = invitations.pending(principal.userId(), principal.requireWorkspaceId());

        Map<UUID, String> inviterNames = users
                .findAllById(pending.stream().map(Invitation::getInvitedBy).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return ResponseEntity.ok(pending.stream()
                .map(inv -> new InvitationResponse(inv.getId(), inv.getEmail(),
                        WorkspaceRole.valueOf(inv.getRole().getName()),
                        inviterNames.get(inv.getInvitedBy()), inv.getCreatedAt(), inv.getExpiresAt()))
                .toList());
    }

    @PostMapping
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'MEMBER_INVITE')")
    public ResponseEntity<Map<String, Integer>> invite(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @RequestBody List<@Valid InviteRequest> requests,
                                                       HttpServletRequest httpRequest) {
        List<Invitation> sent = invitations.invite(principal,
                requests.stream().map(r -> new InviteCommand(r.email(), r.role())).toList(),
                httpRequest);
        return ResponseEntity.ok(Map.of("sent", sent.size()));
    }

    @PostMapping("/{invitationId}/resend")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'MEMBER_INVITE')")
    public ResponseEntity<Void> resend(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID invitationId, HttpServletRequest httpRequest) {
        invitations.resend(principal.userId(), principal.requireWorkspaceId(), invitationId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{invitationId}")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'MEMBER_INVITE')")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID invitationId, HttpServletRequest httpRequest) {
        invitations.revoke(principal.userId(), principal.requireWorkspaceId(), invitationId, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
