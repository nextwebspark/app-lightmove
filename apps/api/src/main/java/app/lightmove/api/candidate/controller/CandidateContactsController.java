package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.UpdateCandidateContactsRequest;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Contact section's own write. Beside the profile's PUT rather than inside it because the
 * section replaces a list, not a field, and the drawer's other sections must not be able to touch
 * it by replaying the profile.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}/contacts")
@RequiredArgsConstructor
public class CandidateContactsController {

    private final CandidateService candidates;

    @PutMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CandidateResponse> replace(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID candidateId,
                                                     @Valid @RequestBody UpdateCandidateContactsRequest request,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidates.replaceContacts(principal.userId(),
                principal.requireWorkspaceId(), projectId, candidateId, request, httpRequest));
    }
}
