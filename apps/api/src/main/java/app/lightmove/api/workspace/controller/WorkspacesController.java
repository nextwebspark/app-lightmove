package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.controller.AuthResponseAssembler;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.workspace.dto.CreateWorkspaceRequest;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A further workspace, founded from Settings by staff of the current one — so not from a client's portal
 * seat. Answers with the new one as {@code workspace}; the session moves only when the SPA switches.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspacesController {

    private final OnboardingService onboarding;
    private final AuthResponseAssembler assembler;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public UserResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody CreateWorkspaceRequest request,
                               HttpServletRequest httpRequest) {
        Workspace workspace = onboarding.createWorkspace(principal.userId(), request.toCommand(), httpRequest);
        return assembler.userIn(principal.userId(), workspace.getId());
    }
}
