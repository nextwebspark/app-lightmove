package app.lightmove.api.talentmap.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.talentmap.dto.TalentMapConfigResponse;
import app.lightmove.api.talentmap.dto.TalentMapLocationsResponse;
import app.lightmove.api.talentmap.dto.TalentMapResponse;
import app.lightmove.api.talentmap.service.TalentMapService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The map view's reads. The stage reads are {@code WORK_VIEW}, the grid's gate. The config read has no
 * {@code @PreAuthorize}: {@code /api/v1/**} requires a verified principal, and a pure {@code CLIENT}
 * holds no workspace action to name yet must be told the map exists.
 */
@RestController
@RequiredArgsConstructor
public class TalentMapController {

    private final TalentMapService talentMap;

    @GetMapping("/api/v1/projects/{projectId}/talent-map")
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public TalentMapResponse read(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable UUID projectId,
                                  @RequestParam(required = false) String status) {
        return talentMap.read(principal.requireWorkspaceId(), projectId, status);
    }

    @GetMapping("/api/v1/projects/{projectId}/talent-map/locations")
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public TalentMapLocationsResponse locations(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId,
                                                @RequestParam(required = false) String status) {
        return talentMap.readLocations(principal.requireWorkspaceId(), projectId, status);
    }

    @GetMapping("/api/v1/talent-map/config")
    public TalentMapConfigResponse config() {
        return talentMap.config();
    }
}
