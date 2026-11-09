package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.DataProtectionConfigResponse;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** No {@code @PreAuthorize}: {@code /api/v1/**} already requires a verified principal. */
@RestController
@RequiredArgsConstructor
public class DataProtectionConfigController {

    private final LightMoveProperties properties;

    @GetMapping("/api/v1/data-protection/config")
    public DataProtectionConfigResponse config() {
        DataProtectionSettings settings = properties.dataProtection();
        return new DataProtectionConfigResponse(settings != null && settings.enabled());
    }
}
