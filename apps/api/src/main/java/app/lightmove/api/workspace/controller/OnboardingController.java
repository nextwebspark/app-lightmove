package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.controller.AuthResponseAssembler;
import app.lightmove.api.core.security.dto.AuthDtos.AuthResponse;
import app.lightmove.api.core.security.dto.AuthDtos.UserResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.service.AuthService;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.workspace.dto.WorkspaceDtos.AcceptInvitationRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.AcceptInvitationSignupRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.CreateWorkspaceRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.InviteRequest;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import app.lightmove.api.workspace.model.InviteCommand;
import app.lightmove.api.workspace.model.PendingOnboarding;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.service.InvitationService;
import app.lightmove.api.workspace.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signup steps 2 and 3, and invitation redemption.
 *
 * <p>The only authenticated area a user with no workspace can reach — see {@code SecurityConfig}.
 * Everything else in the API needs a tenant claim, which nobody has until they create a workspace or
 * accept an invitation. Those are the only two ways in; there is no join request.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboarding;
    private final InvitationService invitations;
    private final AuthService auth;
    private final AuthResponseAssembler assembler;
    private final RefreshCookieFactory refreshCookie;

    /**
     * Signup step 2 — create your workspace. You are its ADMIN.
     *
     * <p>Returns the updated user, now with a workspace. The client must then call {@code /auth/refresh}
     * to pick up an access token that actually carries the new tenant claim — the one it holds was
     * minted before the workspace existed and says the user has none.
     */
    @PostMapping("/workspace")
    public ResponseEntity<UserResponse> createWorkspace(@Valid @RequestBody CreateWorkspaceRequest request,
                                                        HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();

        Optional<Workspace> created = onboarding.createWorkspace(
                principal.userId(),
                new CreateWorkspaceCommand(request.name(), request.companySize(),
                        request.primaryRegion(), request.teamFocus()),
                httpRequest);

        // 202 while unverified: answers are held and the workspace is created at verification (see
        // PendingOnboarding). The body carries the truth too — workspace null, emailVerified false —
        // because the SPA's request helper returns the parsed body, not the status, and routes on it.
        HttpStatus status = created.isPresent() ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(currentUser(principal));
    }

    /**
     * Corrects a workspace you already run — which is what "Back" means once step 2 has committed.
     *
     * <p>The workspace id comes from the principal, never from the request: {@code requireWorkspaceId()}
     * is the only supported way to learn which tenant a caller belongs to, and accepting it as a
     * parameter would let anyone edit anyone's organisation by guessing an id.
     */
    @PatchMapping("/workspace")
    public ResponseEntity<UserResponse> updateWorkspace(@Valid @RequestBody CreateWorkspaceRequest request,
                                                        HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();

        CreateWorkspaceCommand command = new CreateWorkspaceCommand(
                request.name(), request.companySize(), request.primaryRegion(),
                request.teamFocus());

        // No workspace yet means the wizard is still held awaiting verification, so "Back" edits a draft
        // and createWorkspace already upserts it.
        if (!principal.hasWorkspace()) {
            onboarding.createWorkspace(principal.userId(), command, httpRequest);
            return ResponseEntity.accepted().body(currentUser(principal));
        }

        onboarding.updateWorkspace(principal.userId(), principal.requireWorkspaceId(), command, httpRequest);

        return ResponseEntity.ok(currentUser(principal));
    }

    /**
     * Signup step 3 — invite colleagues. Optional; "Skip for now" simply never calls this.
     *
     * @return how many invitations went out. Fewer than asked for means some recipients were already
     *         members, which is not an error.
     */
    @PostMapping("/invitations")
    public ResponseEntity<InviteResult> invite(@Valid @RequestBody List<InviteRequest> requests,
                                               HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();

        // Held, not sent, while the user is unverified — they have no workspace to invite anyone into,
        // and "email these five people on my say-so" is precisely the action an unproven account must
        // not be able to take. They go out when the workspace is created at verification.
        List<PendingOnboarding.PendingInvite> held = requests.stream()
                // The mockup's dropdown defaults to Member; an omitted role must not become null.
                .map(r -> new PendingOnboarding.PendingInvite(
                        r.email(), r.role() == null ? WorkspaceRole.MEMBER : r.role()))
                .toList();

        if (onboarding.holdInvitations(principal.userId(), held)) {
            return ResponseEntity.accepted().body(new InviteResult(held.size()));
        }

        List<InviteCommand> commands = held.stream()
                .map(i -> new InviteCommand(i.email(), i.role()))
                .toList();

        int sent = invitations.invite(principal, commands, httpRequest).size();
        return ResponseEntity.ok(new InviteResult(sent));
    }

    /**
     * What an invitation link leads to, readable before the invitee has an account.
     *
     * <p>Anonymous on purpose — see {@code InvitationService.preview}. Someone arriving from their inbox
     * has to be told what they are being offered, and the signup form has to know which address the
     * invitation names so it can pin the field there. Without this the invitee signs up with whatever
     * address they like, and acceptance then refuses them for an email mismatch they were never shown.
     */
    @GetMapping("/invitations/preview")
    public ResponseEntity<InvitationService.InvitationPreview> previewInvitation(
            @RequestParam("token") String token) {
        return ResponseEntity.ok(invitations.preview(token));
    }

    /**
     * Redeems an invitation link. The invitee lands ACTIVE immediately — an admin naming them was the
     * approval.
     */
    @PostMapping("/invitations/accept")
    public ResponseEntity<UserResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
                                                         HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        invitations.accept(request.token(), principal.userId(), httpRequest);
        return ResponseEntity.ok(currentUser(principal));
    }

    /**
     * Redeems the caller's own outstanding invitation, with no token.
     *
     * <p>Exists for the invitee who verifies their email in a fresh tab: the emailed invitation token
     * lives in another tab's sessionStorage, but the server already knows a redeemable invitation is
     * addressed to this verified email — {@code /auth/me} says so via {@code pendingInvitation} — and a
     * verified address is the very thing the token existed to prove.
     */
    @PostMapping("/accept-invitation")
    public ResponseEntity<UserResponse> acceptPendingInvitation(HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        invitations.acceptForUser(principal.userId(), httpRequest);
        return ResponseEntity.ok(currentUser(principal));
    }

    /**
     * Accept an invitation by creating the invited account in one step — the door in for an invitee who
     * has no account yet. Public: they have no session to authenticate with, and the invitation token in
     * the body is the credential. Returns a full session — access token in the body, refresh token in
     * the httpOnly cookie — so they land in the workspace with no second login and no verification step.
     */
    @PostMapping("/accept-invitation-signup")
    public ResponseEntity<AuthResponse> acceptInvitationSignup(
            @Valid @RequestBody AcceptInvitationSignupRequest request, HttpServletRequest httpRequest) {
        AuthenticatedSession session = invitations.acceptWithNewLocalUser(
                request.token(), request.fullName(), request.password(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.tokens().refreshToken()).toString())
                .body(assembler.assemble(session.tokens(), session.user(), session.membership()));
    }

    private UserResponse currentUser(AuthPrincipal principal) {
        User user = auth.requireUser(principal.userId());
        WorkspaceMember membership = auth.activeMembership(user.getId()).orElse(null);
        return assembler.user(user, membership);
    }

    public record InviteResult(int sent) {
    }
}
