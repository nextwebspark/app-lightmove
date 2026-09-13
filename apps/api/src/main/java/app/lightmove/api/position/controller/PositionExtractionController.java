package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.position.dto.PositionExtractionResponse;
import app.lightmove.api.position.service.PositionExtractionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the document already attached to a mandate's brief — "Read from document" on step one.
 *
 * <p>Gated {@code PROJECT_EDIT}, not {@code WORK_VIEW} like the document's own download: downloading
 * moves bytes for free, this spends a billed model call (or, degraded, a bit of CPU), and a read-only
 * client seat must not be able to run either up. It is also an explicit act and never a side effect
 * of upload or of the ordinary {@code GET} of the brief — every Replace would otherwise re-bill.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position/document/extract")
@RequiredArgsConstructor
public class PositionExtractionController {

    private final PositionExtractionService extraction;

    @PostMapping("/details")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionExtractionResponse> extractDetails(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(extraction.extractDetails(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    @PostMapping("/context")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionExtractionResponse> extractContext(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(extraction.extractContext(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    @PostMapping("/compensation")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionExtractionResponse> extractCompensation(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(extraction.extractCompensation(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }
}
