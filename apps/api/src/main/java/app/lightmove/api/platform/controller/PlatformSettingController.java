package app.lightmove.api.platform.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.platform.dto.PlatformConfigVersionResponse;
import app.lightmove.api.platform.dto.PlatformSettingResponse;
import app.lightmove.api.platform.dto.PlatformSettingWriteRequest;
import app.lightmove.api.platform.dto.PlatformSettingsResponse;
import app.lightmove.api.platform.service.PlatformConfigService;
import app.lightmove.api.platform.service.PlatformSettingService;
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
 * Settings → Platform → Variables, for a super admin. Gated on the platform action alone, a 404 to anyone else:
 * nothing here reads inside a workspace.
 */
@RestController
@RequestMapping("/api/v1/platform/settings")
@RequiredArgsConstructor
public class PlatformSettingController {

    private final PlatformSettingService settings;
    private final PlatformConfigService config;

    @GetMapping
    @RequirePlatformPermission(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public PlatformSettingsResponse list() {
        return settings.list();
    }

    @PutMapping("/{key}")
    @RequirePlatformPermission(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public PlatformSettingResponse change(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String key,
                                          @Valid @RequestBody PlatformSettingWriteRequest request,
                                          HttpServletRequest httpRequest) {
        return settings.change(principal.userId(), key, request.value(), httpRequest);
    }

    @DeleteMapping("/{key}")
    @RequirePlatformPermission(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public PlatformSettingResponse reset(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String key,
                                         HttpServletRequest httpRequest) {
        return settings.reset(principal.userId(), key, httpRequest);
    }

    @PostMapping("/refresh")
    @RequirePlatformPermission(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public PlatformConfigVersionResponse refresh(@AuthenticationPrincipal AuthPrincipal principal,
                                                 HttpServletRequest httpRequest) {
        config.refresh(principal.userId(), httpRequest);
        return config.version();
    }
}
