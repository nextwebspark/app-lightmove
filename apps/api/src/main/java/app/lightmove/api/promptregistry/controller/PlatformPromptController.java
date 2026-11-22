package app.lightmove.api.promptregistry.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.core.security.rbac.RequireRecentTwoFactor;
import app.lightmove.api.promptregistry.dto.AiModelResponse;
import app.lightmove.api.promptregistry.dto.PromptDetail;
import app.lightmove.api.promptregistry.dto.PromptOverview;
import app.lightmove.api.promptregistry.dto.PromptTrialResponse;
import app.lightmove.api.promptregistry.dto.PromptVersionBody;
import app.lightmove.api.promptregistry.dto.PutPromptLiveRequest;
import app.lightmove.api.promptregistry.dto.SavePromptVersionRequest;
import app.lightmove.api.promptregistry.service.PromptCatalogService;
import app.lightmove.api.promptregistry.service.PromptPromotionService;
import app.lightmove.api.promptregistry.service.PromptTrialService;
import app.lightmove.api.promptregistry.service.PromptVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AI prompts, for a platform super admin: read, save a version and try it under {@code PROMPT_MANAGE}; move what
 * serves under {@code PROMPT_PROMOTE} and a fresh two-factor code. Nothing here reads inside a workspace.
 */
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformPromptController {

    private final PromptCatalogService catalog;
    private final PromptVersionService versions;
    private final PromptPromotionService promotion;
    private final PromptTrialService trials;

    @GetMapping("/prompts")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public List<PromptOverview> list() {
        return catalog.list();
    }

    @GetMapping("/prompts/{promptId}")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PromptDetail get(@PathVariable String promptId) {
        return catalog.get(promptId);
    }

    @GetMapping("/prompts/{promptId}/versions/{version}")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PromptVersionBody body(@PathVariable String promptId, @PathVariable int version) {
        return catalog.body(promptId, version);
    }

    @PostMapping("/prompts/{promptId}/versions")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public PromptDetail save(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String promptId,
                             @Valid @RequestBody SavePromptVersionRequest request, HttpServletRequest httpRequest) {
        return versions.save(principal.userId(), promptId, request, httpRequest);
    }

    @PostMapping("/prompts/{promptId}/versions/{version}/trial")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public PromptTrialResponse tryIt(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String promptId,
                                     @PathVariable int version, HttpServletRequest httpRequest) {
        return trials.tryIt(principal.userId(), promptId, version, httpRequest);
    }

    @PutMapping("/prompts/{promptId}/production")
    @RequireRecentTwoFactor(PlatformAction.PROMPT_PROMOTE)
    public PromptDetail putLive(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String promptId,
                                @Valid @RequestBody PutPromptLiveRequest request, HttpServletRequest httpRequest) {
        return promotion.putLive(principal.userId(), promptId, request, httpRequest);
    }

    @GetMapping("/ai-models")
    @RequirePlatformPermission(PlatformAction.PROMPT_MANAGE)
    public List<AiModelResponse> models() {
        return catalog.models();
    }
}
