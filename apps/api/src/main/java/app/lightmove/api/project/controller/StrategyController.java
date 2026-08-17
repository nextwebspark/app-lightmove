package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.project.dto.PutCompanySizeRequest;
import app.lightmove.api.project.dto.PutGeographyRequest;
import app.lightmove.api.project.dto.PutOffLimitsRequest;
import app.lightmove.api.project.dto.PutOwnershipRequest;
import app.lightmove.api.project.dto.PutSectorsRequest;
import app.lightmove.api.project.dto.PutTargetsRequest;
import app.lightmove.api.project.dto.StrategyResponse;
import app.lightmove.api.project.service.StrategyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The search strategy of one mandate. Reading needs a seat on the project (WORK_EXECUTE, which every
 * project role holds), with the workspace-admin bypass so an admin sees every project — a mandate's
 * scope is team content, not browsable to the whole workspace. Writing it is PROJECT_EDIT on the seat.
 * The workspace comes from the principal, never the path.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategy;

    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<StrategyResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId) {
        return ResponseEntity.ok(strategy.get(principal.requireWorkspaceId(), projectId));
    }

    @PutMapping("/sectors")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putSectors(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID projectId,
                                                       @Valid @RequestBody PutSectorsRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putSectors(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/company-size")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putCompanySize(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable UUID projectId,
                                                           @Valid @RequestBody PutCompanySizeRequest request,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putCompanySize(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/geography")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putGeography(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody PutGeographyRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putGeography(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/ownership")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putOwnership(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody PutOwnershipRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putOwnership(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/targets")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putTargets(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID projectId,
                                                       @Valid @RequestBody PutTargetsRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putTargets(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/off-limits")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putOffLimits(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody PutOffLimitsRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putOffLimits(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }
}
