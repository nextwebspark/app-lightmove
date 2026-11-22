package app.lightmove.api.startpage.controller;

import app.lightmove.api.core.security.dto.StartPageResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.startpage.dto.SaveStartPageRequest;
import app.lightmove.api.startpage.service.StartPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own start page in this workspace. Every member has one, a pure client included. */
@RestController
@RequestMapping("/api/v1/me/start-page")
@RequiredArgsConstructor
@PreAuthorize("@workspaceAuthorizer.member(principal)")
public class StartPageController {

    private final StartPageService startPages;

    @GetMapping
    public StartPageResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return startPages.read(principal.userId(), principal.requireWorkspaceId());
    }

    @PutMapping
    public StartPageResponse save(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody SaveStartPageRequest request) {
        return startPages.save(principal.userId(), principal.requireWorkspaceId(), request);
    }
}
