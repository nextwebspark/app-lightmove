package app.lightmove.api.enrichment.sourcing.controller;

import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingConfigResponse;
import app.lightmove.api.enrichment.sourcing.service.ExecutiveSourcingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether the In-universe page offers Find executives, and the numbers its dialog states. No
 * {@code @PreAuthorize} for {@code ContactLookupConfigController}'s reason.
 */
@RestController
@RequiredArgsConstructor
public class ExecutiveSourcingConfigController {

    private final ExecutiveSourcingService sourcing;

    @GetMapping("/api/v1/executive-sourcing/config")
    public ExecutiveSourcingConfigResponse config() {
        return sourcing.config();
    }
}
