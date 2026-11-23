package app.lightmove.api.platformsettings.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.core.security.rbac.RequireRecentTwoFactor;
import app.lightmove.api.platformsettings.dto.ChangePlatformSettingRequest;
import app.lightmove.api.platformsettings.dto.PlatformSettingChangeResponse;
import app.lightmove.api.platformsettings.dto.PlatformSettingResponse;
import app.lightmove.api.platformsettings.dto.ResetPlatformSettingRequest;
import app.lightmove.api.platformsettings.service.PlatformSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The application variables, for a platform super admin: read under {@code PLATFORM_SETTINGS_MANAGE}; a change or a
 * reset reaches every workspace, so each also asks a fresh two-factor code. Nothing here reads inside a workspace.
 */
@RestController
@RequestMapping("/api/v1/platform/settings")
@RequiredArgsConstructor
public class PlatformSettingController {

    private final PlatformSettingService settings;

    @GetMapping
    @RequirePlatformPermission(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public List<PlatformSettingResponse> list() {
        return settings.list();
    }

    @GetMapping("/{key}/history")
    @RequirePlatformPermission(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public List<PlatformSettingChangeResponse> history(@PathVariable String key) {
        return settings.history(key);
    }

    @PutMapping("/{key}")
    @RequireRecentTwoFactor(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public PlatformSettingResponse change(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String key,
                                          @Valid @RequestBody ChangePlatformSettingRequest request,
                                          HttpServletRequest httpRequest) {
        return settings.change(principal.userId(), key, request, httpRequest);
    }

    @PostMapping("/{key}/reset")
    @RequireRecentTwoFactor(PlatformAction.PLATFORM_SETTINGS_MANAGE)
    public PlatformSettingResponse reset(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String key,
                                         @Valid @RequestBody ResetPlatformSettingRequest request,
                                         HttpServletRequest httpRequest) {
        return settings.reset(principal.userId(), key, request, httpRequest);
    }
}
