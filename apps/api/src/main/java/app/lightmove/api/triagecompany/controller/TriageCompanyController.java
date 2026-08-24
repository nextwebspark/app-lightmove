package app.lightmove.api.triagecompany.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
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
 * A mandate's triaged companies. Gated per method rather than per class: reading is WORK_VIEW, held
 * by every seated role including CLIENT, while every write is WORK_EXECUTE — a client representative
 * may see that a company was shortlisted without being able to shortlist one.
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
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size) {
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
     * The browser extension's write. Same gate and the same table as {@link #add} — a capture is a
     * triage decision like any other — but it accepts a company the Apollo universe does not publish,
     * which is the ordinary case when a consultant is reading a GCC company's own website.
     */
    @PostMapping("/captures")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<TriageCompanyResponse> capture(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody CaptureCompanyRequest request,
                                                         HttpServletRequest httpRequest) {
        TriageCompanyResponse captured = triage.capture(principal.userId(),
                principal.requireWorkspaceId(), projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(captured);
    }

    /** Takes no body: the scope is the stored filter, so a request cannot ask for a wider one. */
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
