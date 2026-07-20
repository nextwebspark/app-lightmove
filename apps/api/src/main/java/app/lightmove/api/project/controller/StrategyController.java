package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.StrategyDtos.PutSectorsRequest;
import app.lightmove.api.project.dto.StrategyDtos.StrategyResponse;
import app.lightmove.api.project.service.StrategyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<StrategyResponse> get(@PathVariable UUID projectId) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(strategy.get(principal.requireWorkspaceId(), projectId));
    }

    @PutMapping("/sectors")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putSectors(@PathVariable UUID projectId,
                                                       @Valid @RequestBody PutSectorsRequest request,
                                                       HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(strategy.putSectors(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }
}
