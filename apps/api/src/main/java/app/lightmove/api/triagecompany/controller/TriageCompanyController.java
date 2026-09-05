package app.lightmove.api.triagecompany.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.EditCustomFieldsRequest;
import app.lightmove.api.triagecompany.dto.EditTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A mandate's triaged companies. Gated per method rather than per class: reading is WORK_VIEW, held
 * by every seated role including CLIENT, while every write is WORK_EXECUTE — a client representative
 * may see that a company was shortlisted without being able to shortlist one, capture one, or remove
 * one from the mandate.
 *
 * <p>The three Companies screens are three reads of {@code GET} at different statuses, and every move
 * between them is the one {@code PATCH}. Neither needed an endpoint of its own: a stage is a value,
 * not a resource, and giving each its own route would have made "shortlist this" and "decline this"
 * two implementations of one write.
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
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String sort,
                                                        @RequestParam(required = false) String direction,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size) {
        TriageCompanyListCriteria criteria =
                new TriageCompanyListCriteria(status, q, sort, direction, page, size);
        return ResponseEntity.ok(triage.list(principal.requireWorkspaceId(), projectId, criteria));
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
     * A company the market does not carry — typed in on the Companies screen, or captured off a live
     * page by the browser plugin. Separate from {@code POST /} because the trust model is the
     * opposite: there the client names an id and the server resolves every field, here the client
     * carries the fields and the row records that it did.
     */
    @PostMapping("/capture")
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

    /**
     * Replaces a company's own facts — the Companies panel's Edit form.
     *
     * <p>A PUT beside the PATCH above rather than more fields on it, because the two mean different
     * things: the PATCH is a triage change where a null leaves the other half alone, and this is a
     * whole form where an omitted field is a cleared one. Refused outright for a company taken from
     * the market, whose fields belong to the export rather than to the mandate.
     */
    @PutMapping("/{triageCompanyId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<TriageCompanyResponse> edit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID triageCompanyId,
            @Valid @RequestBody EditTriageCompanyRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(triage.edit(principal.userId(), principal.requireWorkspaceId(),
                projectId, triageCompanyId, request, httpRequest));
    }

    /**
     * Removes this mandate's decision about a company. The company itself is untouched — the Apollo
     * universe is read-only to this application — so it stays on Strategy and stays available to every
     * other mandate.
     */
    /**
     * The mandate's own columns for one company — the only edit a market-sourced company accepts.
     *
     * <p>Its own route rather than fields on the PUT above, because that one is refused outright for a
     * company taken from the market and this one must not be: the export's facts are not the mandate's
     * to rewrite, and the columns the mandate added to its own grid are nobody else's.
     */
    @PatchMapping("/{triageCompanyId}/custom-fields")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<TriageCompanyResponse> editCustomFields(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID triageCompanyId,
            @Valid @RequestBody EditCustomFieldsRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(triage.editCustomFields(principal.userId(),
                principal.requireWorkspaceId(), projectId, triageCompanyId, request.customFields(),
                httpRequest));
    }

    @DeleteMapping("/{triageCompanyId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID triageCompanyId,
                                       HttpServletRequest httpRequest) {
        triage.removeFromProject(principal.userId(), principal.requireWorkspaceId(), projectId,
                triageCompanyId, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
