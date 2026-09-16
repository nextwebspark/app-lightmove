package app.lightmove.api.enrichment.contact.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.enrichment.contact.dto.ContactLookupResponse;
import app.lightmove.api.enrichment.contact.service.ContactLookupService;
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
 * The drawer's Find email and Find phone buttons.
 *
 * <p>Two endpoints rather than one taking a channel: they spend from separate pools, so one can be
 * refused for want of credits while the other still answers, and a single response could not say so.
 *
 * <p>WORK_EXECUTE rather than WORK_VIEW even though a lookup reads a person — it writes to the row and
 * it spends money, and a client representative holds WORK_VIEW.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}/contact")
@RequiredArgsConstructor
public class ContactLookupController {

    private final ContactLookupService lookups;

    @PostMapping("/email")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<ContactLookupResponse> findEmail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(lookups.findEmail(principal.userId(), principal.requireWorkspaceId(),
                projectId, candidateId, httpRequest));
    }

    @PostMapping("/phone")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<ContactLookupResponse> findPhone(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(lookups.findPhone(principal.userId(), principal.requireWorkspaceId(),
                projectId, candidateId, httpRequest));
    }
}
