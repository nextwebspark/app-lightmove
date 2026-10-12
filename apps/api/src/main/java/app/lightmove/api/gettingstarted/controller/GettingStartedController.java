package app.lightmove.api.gettingstarted.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.gettingstarted.constant.GettingStartedStep;
import app.lightmove.api.gettingstarted.dto.DismissGettingStartedRequest;
import app.lightmove.api.gettingstarted.dto.GettingStartedResponse;
import app.lightmove.api.gettingstarted.dto.SkipGettingStartedStepRequest;
import app.lightmove.api.gettingstarted.service.GettingStartedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** My positions' Getting started card. Staff only: a client representative has no search to set up. */
@RestController
@RequestMapping("/api/v1/workspace/getting-started")
@RequiredArgsConstructor
public class GettingStartedController {

    private final GettingStartedService gettingStarted;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public GettingStartedResponse view(@AuthenticationPrincipal AuthPrincipal principal) {
        return gettingStarted.view(principal.userId(), principal.requireWorkspaceId());
    }

    @PutMapping("/dismissed")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public GettingStartedResponse setDismissed(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody DismissGettingStartedRequest request) {
        return gettingStarted.setDismissed(principal.userId(), principal.requireWorkspaceId(), request.dismissed());
    }

    @PutMapping("/steps/{step}/skipped")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public GettingStartedResponse setSkipped(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable GettingStartedStep step,
                                             @Valid @RequestBody SkipGettingStartedStepRequest request) {
        return gettingStarted.setSkipped(principal.userId(), principal.requireWorkspaceId(), step, request.skipped());
    }
}
