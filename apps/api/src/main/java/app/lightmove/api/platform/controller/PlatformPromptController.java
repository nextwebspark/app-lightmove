package app.lightmove.api.platform.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.platform.dto.PlatformConfigVersionResponse;
import app.lightmove.api.platform.dto.PlatformPromptDetail;
import app.lightmove.api.platform.dto.PlatformPromptWriteRequest;
import app.lightmove.api.platform.dto.PlatformPromptsResponse;
import app.lightmove.api.platform.service.PlatformConfigService;
import app.lightmove.api.platform.service.PlatformPromptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Platform → Prompts, for a super admin. Gated on the platform action alone, a 404 to anyone else. No
 * route here runs a prompt or reads a workspace's people.
 */
@RestController
@RequestMapping("/api/v1/platform/prompts")
@RequiredArgsConstructor
public class PlatformPromptController {

    private final PlatformPromptService prompts;
    private final PlatformConfigService config;

    @GetMapping
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PlatformPromptsResponse list() {
        return prompts.list();
    }

    @GetMapping("/{promptId}")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PlatformPromptDetail get(@PathVariable String promptId) {
        return prompts.get(promptId);
    }

    @PutMapping("/{promptId}")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PlatformPromptDetail save(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String promptId,
                                     @Valid @RequestBody PlatformPromptWriteRequest request,
                                     HttpServletRequest httpRequest) {
        return prompts.save(principal.userId(), promptId, request.body(), request.expectedVersion(), httpRequest);
    }

    @PostMapping("/{promptId}/versions/{version}/restore")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PlatformPromptDetail restore(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable String promptId, @PathVariable int version,
                                        HttpServletRequest httpRequest) {
        return prompts.restore(principal.userId(), promptId, version, httpRequest);
    }

    @DeleteMapping("/{promptId}/override")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PlatformPromptDetail revert(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String promptId, HttpServletRequest httpRequest) {
        return prompts.revert(principal.userId(), promptId, httpRequest);
    }

    @PostMapping("/refresh")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PlatformConfigVersionResponse refresh(@AuthenticationPrincipal AuthPrincipal principal,
                                                 HttpServletRequest httpRequest) {
        config.refresh(principal.userId(), httpRequest);
        return config.version();
    }
}
