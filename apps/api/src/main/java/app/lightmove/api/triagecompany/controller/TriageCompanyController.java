package app.lightmove.api.triagecompany.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A mandate's triaged companies — one resource, because a triage row is one thing whether you are
 * reading it or moving it.
 *
 * <p><b>The two halves of the seat are gated differently, which is why the annotations are per
 * method rather than on the class.</b> Reading is WORK_VIEW, held by every seated role including
 * CLIENT: what a team has shortlisted reveals its thinking as directly as the filter does, so it gets
 * a team-only gate rather than the workspace-level PROJECT_BROWSE that {@code CompanySearchController}
 * uses for the shape of the market itself. Every write is WORK_EXECUTE — a client representative who
 * follows a mandate must not be able to add a company to it or move one to Declined.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/triage")
@RequiredArgsConstructor
public class TriageCompanyController {

    private final TriageCompanyService triage;

    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<TriageCompaniesResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable UUID projectId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(triage.list(principal.requireWorkspaceId(), projectId, status,
                page, size));
    }

    @PostMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<TriageCompanyResponse> add(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable UUID projectId,
                                                     @Valid @RequestBody AddTriageCompanyRequest request,
                                                     HttpServletRequest httpRequest) {
        TriageCompanyResponse added = triage.add(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    /**
     * "Add all to Universe" — everything the mandate's saved filter matches, up to the configured cap.
     * Takes no body: the scope is the stored filter, so a request cannot ask for a wider one than the
     * screen is showing.
     */
    @PostMapping("/from-filter")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<TriageBulkAddResponse> addAllInScope(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @PathVariable UUID projectId,
                                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(triage.addAllInScope(principal.userId(),
                principal.requireWorkspaceId(), projectId, httpRequest));
    }

    @PatchMapping("/{triageCompanyId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<TriageCompanyResponse> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID triageCompanyId,
            @Valid @RequestBody UpdateTriageCompanyRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(triage.update(principal.userId(), principal.requireWorkspaceId(),
                projectId, triageCompanyId, request, httpRequest));
    }
}
