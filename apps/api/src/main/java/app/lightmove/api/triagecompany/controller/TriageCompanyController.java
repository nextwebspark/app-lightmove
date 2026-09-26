package app.lightmove.api.triagecompany.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.triagecompany.dto.AddSelectedTriageCompaniesRequest;
import app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.EditCustomFieldsRequest;
import app.lightmove.api.triagecompany.dto.EditTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyListCriteria;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.dto.UpdateTriageCompanyRequest;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A mandate's triaged companies. Reads are WORK_VIEW (a client seat included), every write WORK_EXECUTE. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/triage")
@RequiredArgsConstructor
public class TriageCompanyController {

    private final TriageCompanyService triage;
    private final TriageCompanyReadService reads;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public TriageCompaniesResponse list(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String executiveQuery,
                                        @RequestParam(required = false) List<String> executiveStatuses,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(required = false) String direction,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        TriageCompanyListCriteria criteria = new TriageCompanyListCriteria(
                status, q, executiveQuery, executiveStatuses, sort, direction, page, size);
        return reads.list(principal.requireWorkspaceId(), projectId, criteria);
    }

    @PostMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public TriageCompanyResponse add(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID projectId,
                                     @Valid @RequestBody AddTriageCompanyRequest request,
                                     HttpServletRequest httpRequest) {
        TriageCompanyResponse added = triage.add(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return added;
    }

    /** The opposite trust model to {@code POST /}: the client carries the fields, and the row says so. */
    @PostMapping("/capture")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public TriageCompanyResponse capture(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId,
                                         @Valid @RequestBody CaptureCompanyRequest request,
                                         HttpServletRequest httpRequest) {
        TriageCompanyResponse captured = triage.capture(principal.userId(),
                principal.requireWorkspaceId(), projectId, request, httpRequest);
        return captured;
    }

    /** Takes no body: the scope is the stored filter, so a request cannot ask for a wider one. */
    @PostMapping("/from-filter")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TriageBulkAddResponse addAllInScope(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId,
                                               HttpServletRequest httpRequest) {
        return triage.addAllInScope(principal.userId(),
                principal.requireWorkspaceId(), projectId, httpRequest);
    }

    @PostMapping("/bulk")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TriageBulkAddResponse addSelected(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId,
                                             @Valid @RequestBody AddSelectedTriageCompaniesRequest request,
                                             HttpServletRequest httpRequest) {
        return triage.addSelected(principal.userId(),
                principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PatchMapping("/{triageCompanyId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TriageCompanyResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID triageCompanyId,
            @Valid @RequestBody UpdateTriageCompanyRequest request,
            HttpServletRequest httpRequest) {
        return triage.update(principal.userId(), principal.requireWorkspaceId(),
                projectId, triageCompanyId, request, httpRequest);
    }

    /** Unlike the PATCH, an omitted field is cleared. Refused for a company taken from the market. */
    @PutMapping("/{triageCompanyId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TriageCompanyResponse edit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID triageCompanyId,
            @Valid @RequestBody EditTriageCompanyRequest request,
            HttpServletRequest httpRequest) {
        return triage.edit(principal.userId(), principal.requireWorkspaceId(),
                projectId, triageCompanyId, request, httpRequest);
    }

    /** The mandate's own columns — the only edit a market-sourced company accepts. */
    @PatchMapping("/{triageCompanyId}/custom-fields")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TriageCompanyResponse editCustomFields(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID triageCompanyId,
            @Valid @RequestBody EditCustomFieldsRequest request,
            HttpServletRequest httpRequest) {
        return triage.editCustomFields(principal.userId(),
                principal.requireWorkspaceId(), projectId, triageCompanyId, request.customFields(),
                httpRequest);
    }

    @DeleteMapping("/{triageCompanyId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable UUID projectId,
                       @PathVariable UUID triageCompanyId,
                       HttpServletRequest httpRequest) {
        triage.removeFromProject(principal.userId(), principal.requireWorkspaceId(), projectId,
                triageCompanyId, httpRequest);
    }
}
