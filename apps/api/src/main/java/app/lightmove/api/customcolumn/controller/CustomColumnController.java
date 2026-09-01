package app.lightmove.api.customcolumn.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.dto.CustomColumnsResponse;
import app.lightmove.api.customcolumn.dto.DefineCustomColumnRequest;
import app.lightmove.api.customcolumn.dto.ReorderCustomColumnsRequest;
import app.lightmove.api.customcolumn.dto.UpdateCustomColumnRequest;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A mandate's custom grid columns. Gated the same way the triage and candidate writes are: reading is
 * WORK_VIEW, so a client representative sees the same headers the team does, and every change is
 * WORK_EXECUTE — the shape of a mandate's grid is the team's to decide.
 *
 * <p>Only definitions live here. A column's <i>values</i> travel on the row that holds them, saved in
 * the same request as every built-in field beside them, because that is where a user edits them.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/custom-columns")
@RequiredArgsConstructor
public class CustomColumnController {

    private final CustomColumnService customColumns;

    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<CustomColumnsResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable UUID projectId) {
        return ResponseEntity.ok(customColumns.list(principal.requireWorkspaceId(), projectId));
    }

    @PostMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CustomColumnDto> define(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID projectId,
                                                  @Valid @RequestBody DefineCustomColumnRequest request,
                                                  HttpServletRequest httpRequest) {
        CustomColumnDto defined = customColumns.define(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(defined);
    }

    @PatchMapping("/{columnId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CustomColumnDto> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID projectId,
                                                  @PathVariable UUID columnId,
                                                  @Valid @RequestBody UpdateCustomColumnRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(customColumns.update(principal.userId(), principal.requireWorkspaceId(),
                projectId, columnId, request, httpRequest));
    }

    /**
     * PUT rather than PATCH: the body is the whole new order of one grid's columns, not a change to
     * one of them, and applying it twice leaves the same result.
     */
    @PutMapping("/order")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CustomColumnsResponse> reorder(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody ReorderCustomColumnsRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(customColumns.reorder(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest));
    }

    @DeleteMapping("/{columnId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID columnId,
                                       HttpServletRequest httpRequest) {
        customColumns.remove(principal.userId(), principal.requireWorkspaceId(), projectId, columnId,
                httpRequest);
        return ResponseEntity.noContent().build();
    }
}
