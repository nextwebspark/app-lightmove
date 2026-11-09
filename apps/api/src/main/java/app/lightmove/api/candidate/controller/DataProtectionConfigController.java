package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.DataProtectionConfigResponse;
import app.lightmove.api.candidate.service.DataProtectionControls;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A deployment fact the drawer and Settings read once. No {@code @PreAuthorize}: {@code /api/v1/**} already requires
 * a verified principal, and a pure CLIENT holds no workspace action to name.
 */
@RestController
@RequiredArgsConstructor
public class DataProtectionConfigController {

    private final DataProtectionControls controls;

    @GetMapping("/api/v1/data-protection/config")
    public DataProtectionConfigResponse config() {
        return new DataProtectionConfigResponse(controls.isOffered());
    }
}
