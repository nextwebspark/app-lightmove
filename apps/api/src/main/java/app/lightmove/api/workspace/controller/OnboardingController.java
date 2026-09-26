package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.controller.AuthResponseAssembler;
import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.strategy.dto.CompanySuggestionsResponse;
import app.lightmove.api.strategy.service.CompanySuggestionSearch;
import app.lightmove.api.workspace.dto.AcceptInvitationRequest;
import app.lightmove.api.workspace.dto.AcceptInvitationSignupRequest;
import app.lightmove.api.workspace.dto.CreateWorkspaceRequest;
import app.lightmove.api.workspace.dto.InviteRequest;
import app.lightmove.api.workspace.model.InviteCommand;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.service.InvitationService;
import app.lightmove.api.workspace.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The organisation and invite steps of signup, and invitation redemption — the only authenticated
 * area a user with no workspace can reach. Everything else needs a tenant claim, which nobody has
 * until they create a workspace or accept an invitation.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final int MIN_COMPANY_QUERY_LENGTH = 2;

    private final OnboardingService onboarding;
    private final InvitationService invitations;
    private final AuthenticationService authentication;
    private final AuthResponseAssembler assembler;
    private final RefreshCookieFactory refreshCookie;
    private final CompanySuggestionSearch suggestions;
    private final RateLimitGuard rateLimit;

    /**
     * Signup step 3 — create your workspace. Answers with the new workspace as the user's
     * {@code workspace}, but the client must then call {@code /auth/refresh}: the access token it holds
     * was minted before the workspace existed and carries no tenant claim.
     */
    @PostMapping("/workspace")
    public ResponseEntity<UserResponse> createWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody CreateWorkspaceRequest request,
                                                        HttpServletRequest httpRequest) {
        Workspace workspace = onboarding.createFirstWorkspace(principal.userId(), request.toCommand(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.userIn(principal.userId(), workspace.getId()));
    }

    /**
     * Corrects a workspace you already run — what "Back" means once the step has committed. The
     * workspace id comes from the principal: as a parameter it would let anyone edit anyone's.
     */
    @PatchMapping("/workspace")
    public ResponseEntity<UserResponse> updateWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody CreateWorkspaceRequest request,
                                                        HttpServletRequest httpRequest) {
        onboarding.updateWorkspace(principal.userId(), principal.requireWorkspaceId(), request.toCommand(),
                httpRequest);

        return ResponseEntity.ok(currentUser(principal));
    }

    /**
     * The organisation step's company picker. {@code /companies/search} is gated on
     * {@code PROJECT_BROWSE}, which nobody holds before their workspace exists, so the step reads the
     * same universe typeahead here. Existence of a company is not secret, but each query is an
     * unindexable scan reachable by any verified session, so it carries its own per-account and per-IP
     * budget; a query shorter than the picker's minimum answers nothing.
     */
    @GetMapping("/companies")
    public ResponseEntity<CompanySuggestionsResponse> searchCompanies(@AuthenticationPrincipal AuthPrincipal principal,
                                                                      @RequestParam(name = "q") String query,
                                                                      HttpServletRequest httpRequest) {
        rateLimit.checkOnboardingCompanySearch(principal.email(), httpRequest);
        return ResponseEntity.ok(new CompanySuggestionsResponse(
                suggestions.suggest(query, null, MIN_COMPANY_QUERY_LENGTH)));
    }

    /**
     * Signup step 4 — invite colleagues. Optional; "Skip for now" simply never calls this.
     *
     * @return how many invitations went out. Fewer than asked for means some recipients were already
     *         members, which is not an error.
     */
    @PostMapping("/invitations")
    public ResponseEntity<InviteResult> invite(@AuthenticationPrincipal AuthPrincipal principal,
                                               @RequestBody List<@Valid InviteRequest> requests,
                                               HttpServletRequest httpRequest) {
        List<InviteCommand> commands = requests.stream()
                // The mockup's dropdown defaults to Member; an omitted role must not become null.
                .map(r -> new InviteCommand(r.email(), r.role() == null ? WorkspaceRole.MEMBER : r.role()))
                .toList();

        int sent = invitations.invite(principal, commands, httpRequest).size();
        return ResponseEntity.ok(new InviteResult(sent));
    }

    /**
     * What an invitation link leads to, readable before the invitee has an account.
     *
     * <p>Anonymous on purpose — see {@code InvitationService.preview}. The signup form has to know
     * which address the invitation names so it can pin the field there; without it the invitee signs
     * up with any address and acceptance refuses them for a mismatch they were never shown.
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
    public ResponseEntity<UserResponse> acceptInvitation(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody AcceptInvitationRequest request,
                                                         HttpServletRequest httpRequest) {
        WorkspaceMember member = invitations.accept(request.token(), principal.userId(), httpRequest);
        return ResponseEntity.ok(joined(principal, member));
    }

    /**
     * Redeems one of the caller's own outstanding invitations — the ones {@code /me} lists — with no
     * token. For the invitee who verifies in a fresh tab, where the emailed token lives in another
     * tab's sessionStorage, and for a placed user joining a second workspace from the app. A verified
     * matching address is the very thing the token existed to prove; an id that is not addressed to
     * the caller redeems nothing.
     */
    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<UserResponse> acceptInvitationById(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @PathVariable UUID invitationId,
                                                             HttpServletRequest httpRequest) {
        WorkspaceMember member = invitations.acceptById(invitationId, principal.userId(), httpRequest);
        return ResponseEntity.ok(joined(principal, member));
    }

    /**
     * Accept an invitation by creating the invited account in one step. Public: they have no session
     * to authenticate with, and the invitation token in the body is the credential. Returns a full
     * session, so they land in the workspace with no second login and no verification step.
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
        return assembler.userIn(principal.userId(), principal.workspaceId());
    }

    private UserResponse joined(AuthPrincipal principal, WorkspaceMember member) {
        return assembler.user(authentication.requireUser(principal.userId()), member);
    }

    public record InviteResult(int sent) {
    }
}
