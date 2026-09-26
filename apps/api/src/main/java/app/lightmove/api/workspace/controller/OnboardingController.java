package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.controller.AuthResponseAssembler;
import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.strategy.dto.CompanySuggestionsResponse;
import app.lightmove.api.strategy.service.CompanySuggestionSearch;
import app.lightmove.api.workspace.dto.AcceptInvitationRequest;
import app.lightmove.api.workspace.dto.AcceptInvitationSignupRequest;
import app.lightmove.api.workspace.dto.CreateWorkspaceRequest;
import app.lightmove.api.workspace.dto.InviteRequest;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import app.lightmove.api.workspace.model.InviteCommand;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.service.InvitationAcceptService;
import app.lightmove.api.workspace.service.InvitationService;
import app.lightmove.api.workspace.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signup's organisation and invite steps and invitation redemption — the only authenticated area
 * reachable without a tenant claim.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final int MIN_COMPANY_QUERY_LENGTH = 2;

    private final OnboardingService onboarding;
    private final InvitationService invitations;
    private final InvitationAcceptService invitationAccept;
    private final AuthenticationService authentication;
    private final AuthResponseAssembler assembler;
    private final RefreshCookieFactory refreshCookie;
    private final CompanySuggestionSearch suggestions;
    private final RateLimitGuard rateLimit;

    /** The client must then call {@code /auth/refresh}: its access token carries no tenant claim yet. */
    @PostMapping("/workspace")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody CreateWorkspaceRequest request,
                                        HttpServletRequest httpRequest) {
        onboarding.createWorkspace(
                principal.userId(),
                new CreateWorkspaceCommand(request.name(), request.apolloAccountId(), request.companySize(),
                        request.primaryRegion(), request.teamFocus()),
                httpRequest);

        return currentUser(principal);
    }

    /** "Back" after the step committed. The workspace id is the principal's, never a parameter. */
    @PatchMapping("/workspace")
    public UserResponse updateWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody CreateWorkspaceRequest request,
                                        HttpServletRequest httpRequest) {
        CreateWorkspaceCommand command = new CreateWorkspaceCommand(
                request.name(), request.apolloAccountId(), request.companySize(), request.primaryRegion(),
                request.teamFocus());

        onboarding.updateWorkspace(principal.userId(), principal.requireWorkspaceId(), command, httpRequest);

        return currentUser(principal);
    }

    /**
     * The picker before {@code PROJECT_BROWSE} exists. Each query is an unindexable scan any verified
     * session can reach, hence its own per-account and per-IP budget.
     */
    @GetMapping("/companies")
    public CompanySuggestionsResponse searchCompanies(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @RequestParam(name = "q") String query,
                                                      HttpServletRequest httpRequest) {
        rateLimit.checkOnboardingCompanySearch(principal.email(), httpRequest);
        return new CompanySuggestionsResponse(
                suggestions.suggest(query, null, MIN_COMPANY_QUERY_LENGTH));
    }

    /** @return invitations sent; fewer than asked means some were already members, not an error */
    @PostMapping("/invitations")
    public InviteResult invite(@AuthenticationPrincipal AuthPrincipal principal,
                               @RequestBody List<@Valid InviteRequest> requests,
                               HttpServletRequest httpRequest) {
        List<InviteCommand> commands = requests.stream()

                .map(r -> new InviteCommand(r.email(), r.role() == null ? WorkspaceRole.MEMBER : r.role()))
                .toList();

        int sent = invitations.invite(principal, commands, httpRequest).size();
        return new InviteResult(sent);
    }

    /** Anonymous on purpose: the signup form pins the invited address, or acceptance would refuse a mismatch. */
    @GetMapping("/invitations/preview")
    public InvitationAcceptService.InvitationPreview previewInvitation(
            @RequestParam("token") String token) {
        return invitationAccept.preview(token);
    }

    @PostMapping("/invitations/accept")
    public UserResponse acceptInvitation(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody AcceptInvitationRequest request,
                                         HttpServletRequest httpRequest) {
        invitationAccept.accept(request.token(), principal.userId(), httpRequest);
        return currentUser(principal);
    }

    /** Token-less: a verified address is what the token existed to prove. */
    @PostMapping("/accept-invitation")
    public UserResponse acceptPendingInvitation(@AuthenticationPrincipal AuthPrincipal principal,
                                                HttpServletRequest httpRequest) {
        invitationAccept.acceptForUser(principal.userId(), httpRequest);
        return currentUser(principal);
    }

    /** Public: the invitation token in the body is the credential. Answers a full session. */
    @PostMapping("/accept-invitation-signup")
    public ResponseEntity<AuthResponse> acceptInvitationSignup(
            @Valid @RequestBody AcceptInvitationSignupRequest request, HttpServletRequest httpRequest) {
        AuthenticatedSession session = invitationAccept.acceptWithNewLocalUser(
                request.token(), request.fullName(), request.password(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.tokens().refreshToken()).toString())
                .body(assembler.assemble(session.tokens(), session.user(), session.membership()));
    }

    private UserResponse currentUser(AuthPrincipal principal) {
        User user = authentication.requireUser(principal.userId());
        WorkspaceMember membership = authentication.activeMembership(user.getId()).orElse(null);
        return assembler.user(user, membership);
    }

    public record InviteResult(int sent) {
    }
}
