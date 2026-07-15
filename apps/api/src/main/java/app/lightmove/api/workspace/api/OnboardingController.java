package app.lightmove.api.workspace.api;

import app.lightmove.api.auth.api.AuthResponseAssembler;
import app.lightmove.api.auth.api.dto.AuthDtos.AcceptInvitationRequest;
import app.lightmove.api.auth.api.dto.AuthDtos.CreateWorkspaceRequest;
import app.lightmove.api.auth.api.dto.AuthDtos.InviteRequest;
import app.lightmove.api.auth.api.dto.AuthDtos.JoinableWorkspace;
import app.lightmove.api.auth.api.dto.AuthDtos.RequestToJoinRequest;
import app.lightmove.api.auth.api.dto.AuthDtos.UserResponse;
import app.lightmove.api.auth.application.AuthService;
import app.lightmove.api.auth.domain.User;
import app.lightmove.api.common.security.AuthPrincipal;
import app.lightmove.api.common.security.CurrentUser;
import app.lightmove.api.invitation.application.InvitationService;
import app.lightmove.api.invitation.application.InviteCommand;
import app.lightmove.api.workspace.application.CreateWorkspaceCommand;
import app.lightmove.api.workspace.application.OnboardingService;
import app.lightmove.api.workspace.application.WorkspaceSummaries;
import app.lightmove.api.workspace.domain.PendingOnboarding;
import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
 * Signup steps 2 and 3.
 *
 * <p>The only authenticated area a user with no workspace can reach — see {@code SecurityConfig}.
 * Everything else in the API needs a tenant claim, which nobody has until step 2 completes.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboarding;
    private final InvitationService invitations;
    private final AuthService auth;
    private final AuthResponseAssembler assembler;
    private final WorkspaceSummaries summaries;

    /**
     * "Is my firm already on LightMove?" — the workspaces running on this user's email domain.
     *
     * <p>Drives the fork at the top of signup step 2: an empty list means they simply create a
     * workspace; a non-empty one lets them recognise their firm and ask to join instead of
     * accidentally starting a second copy of it.
     *
     * <p>Empty for a consumer email domain even where those are permitted — see
     * {@code EmailAddressValidator.canGroupColleaguesBy}.
     */
    @GetMapping("/workspaces")
    public ResponseEntity<List<JoinableWorkspace>> joinableWorkspaces() {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(summaries.joinable(onboarding.joinableWorkspaces(principal.userId())));
    }

    /**
     * Signup step 2, path A — create your own workspace. You are its ADMIN.
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
                        request.primaryRegion(), request.jobTitle(), request.teamFocus()),
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
                request.jobTitle(), request.teamFocus());

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
     * Signup step 2, path B — ask to join a workspace you found on your domain.
     *
     * <p>202 Accepted, not 200: nothing has been granted. The membership is pending and carries no
     * access whatsoever until an admin approves it. The user's next screen is a waiting state.
     */
    @PostMapping("/join-requests")
    public ResponseEntity<UserResponse> requestToJoin(@Valid @RequestBody RequestToJoinRequest request,
                                                      HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();

        onboarding.requestToJoin(principal.userId(), request.workspaceId(),
                request.requestedRole(), httpRequest);

        // The user response still shows no workspace — because they have none. That is not an omission;
        // it is the truth, and the frontend routes on it.
        return ResponseEntity.accepted().body(currentUser(principal));
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
                // The mockup's dropdown defaults to Consultant; an omitted role must not become null.
                .map(r -> new PendingOnboarding.PendingInvite(
                        r.email(), r.role() == null ? WorkspaceRole.CONSULTANT : r.role()))
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

    private UserResponse currentUser(AuthPrincipal principal) {
        User user = auth.requireUser(principal.userId());
        WorkspaceMember membership = auth.activeMembership(user.getId()).orElse(null);
        return assembler.user(user, membership);
    }

    public record InviteResult(int sent) {
    }
}
