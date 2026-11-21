package app.lightmove.api.dashboard.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.dashboard.dto.HomeLayoutDto;
import app.lightmove.api.dashboard.dto.HomeLayoutResponse;
import app.lightmove.api.dashboard.service.HomeLayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own Home in this workspace. Staff only: a pure client's Home has nothing to arrange. */
@RestController
@RequestMapping("/api/v1/me/home")
@RequiredArgsConstructor
@PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
public class HomeLayoutController {

    private final HomeLayoutService homes;

    @GetMapping
    public HomeLayoutResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return homes.get(principal.userId(), principal.requireWorkspaceId());
    }

    @PutMapping
    public HomeLayoutResponse save(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody HomeLayoutDto request) {
        return homes.save(principal.userId(), principal.requireWorkspaceId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@AuthenticationPrincipal AuthPrincipal principal) {
        homes.reset(principal.userId(), principal.requireWorkspaceId());
    }
}
