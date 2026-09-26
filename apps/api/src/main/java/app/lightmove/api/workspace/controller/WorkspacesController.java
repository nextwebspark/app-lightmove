package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.controller.AuthResponseAssembler;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.service.WorkspaceSelection;
import app.lightmove.api.workspace.dto.CreateWorkspaceRequest;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Workspaces: a further workspace, created from inside the app by someone who already has
 * one. The same creation as signup's organisation step ({@code /onboarding/workspace}), gated
 * differently: <b>staff</b> of the current workspace, so a pure client representative — an outside
 * contact seated on a mandate — cannot found a firm's workspace from a portal seat. The creator is the
 * new workspace's ADMIN, exactly as at signup.
 *
 * <p>Answers with the new workspace as {@code user.workspace}; the caller's session still names the
 * old one until it switches, which is what {@code /auth/switch-workspace} is for.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspacesController {

    private final OnboardingService onboarding;
    private final AuthenticationService authentication;
    private final WorkspaceSelection selection;
    private final AuthResponseAssembler assembler;

    @PostMapping
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody CreateWorkspaceRequest request,
                                               HttpServletRequest httpRequest) {
        Workspace workspace = onboarding.createWorkspace(
                principal.userId(),
                new CreateWorkspaceCommand(request.name(), request.apolloAccountId(), request.companySize(),
                        request.primaryRegion(), request.teamFocus()),
                httpRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.user(
                authentication.requireUser(principal.userId()),
                selection.membershipIn(principal.userId(), workspace.getId()).orElse(null)));
    }
}
