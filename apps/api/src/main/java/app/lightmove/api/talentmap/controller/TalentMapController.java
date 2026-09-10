package app.lightmove.api.talentmap.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.talentmap.dto.TalentMapConfigResponse;
import app.lightmove.api.talentmap.dto.TalentMapLocationsResponse;
import app.lightmove.api.talentmap.dto.TalentMapResponse;
import app.lightmove.api.talentmap.service.TalentMapService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The map view's three reads.
 *
 * <p>Both stage reads are {@code WORK_VIEW}, the same gate as the grid they are another rendering
 * of — a client representative who may read a mandate's companies may read them on a globe. The
 * second answers that same map with the rows left off, for the poll that is waiting on places rather
 * than on people. The config read carries no {@code @PreAuthorize} on purpose: {@code /api/v1/**}
 * already requires a verified principal, and a pure {@code CLIENT} holds no workspace action to name
 * here yet must be told the map exists.
 */
@RestController
@RequiredArgsConstructor
public class TalentMapController {

    private final TalentMapService talentMap;

    @GetMapping("/api/v1/projects/{projectId}/talent-map")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<TalentMapResponse> read(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID projectId,
                                                  @RequestParam(required = false) String status) {
        return ResponseEntity.ok(talentMap.read(principal.requireWorkspaceId(), projectId, status));
    }

    @GetMapping("/api/v1/projects/{projectId}/talent-map/locations")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<TalentMapLocationsResponse> locations(@AuthenticationPrincipal AuthPrincipal principal,
                                                                @PathVariable UUID projectId,
                                                                @RequestParam(required = false) String status) {
        return ResponseEntity.ok(talentMap.readLocations(principal.requireWorkspaceId(), projectId, status));
    }

    @GetMapping("/api/v1/talent-map/config")
    public ResponseEntity<TalentMapConfigResponse> config() {
        return ResponseEntity.ok(talentMap.config());
    }
}
