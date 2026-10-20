package app.lightmove.api.triagecompany.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.triagecompany.dto.CompanyNoteResponse;
import app.lightmove.api.triagecompany.dto.PinCompanyNoteRequest;
import app.lightmove.api.triagecompany.dto.WriteCompanyNoteRequest;
import app.lightmove.api.triagecompany.service.CompanyNoteService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The notes on one of a mandate's companies. WORK_EXECUTE throughout: they are the team's working remarks, so a
 * client seat reaches none of them, as with a person's notes (D1).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/triage/{triageCompanyId}/notes")
@RequiredArgsConstructor
public class CompanyNoteController {

    private final CompanyNoteService notes;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public List<CompanyNoteResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId, @PathVariable UUID triageCompanyId) {
        return notes.list(principal.userId(), principal.requireWorkspaceId(), projectId, triageCompanyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CompanyNoteResponse write(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID projectId, @PathVariable UUID triageCompanyId,
                                     @Valid @RequestBody WriteCompanyNoteRequest request,
                                     HttpServletRequest httpRequest) {
        return notes.write(principal.userId(), principal.requireWorkspaceId(), projectId, triageCompanyId, request,
                httpRequest);
    }

    @PutMapping("/{noteId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CompanyNoteResponse revise(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable UUID projectId, @PathVariable UUID triageCompanyId,
                                      @PathVariable UUID noteId, @Valid @RequestBody WriteCompanyNoteRequest request,
                                      HttpServletRequest httpRequest) {
        return notes.revise(principal.userId(), principal.requireWorkspaceId(), projectId, triageCompanyId, noteId,
                request, httpRequest);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID triageCompanyId, @PathVariable UUID noteId,
                       HttpServletRequest httpRequest) {
        notes.remove(principal.userId(), principal.requireWorkspaceId(), projectId, triageCompanyId, noteId,
                httpRequest);
    }

    @PostMapping("/{noteId}/restore")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CompanyNoteResponse restore(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId, @PathVariable UUID triageCompanyId,
                                       @PathVariable UUID noteId, HttpServletRequest httpRequest) {
        return notes.restore(principal.userId(), principal.requireWorkspaceId(), projectId, triageCompanyId, noteId,
                httpRequest);
    }

    @PatchMapping("/{noteId}/pin")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CompanyNoteResponse pin(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                   @PathVariable UUID triageCompanyId, @PathVariable UUID noteId,
                                   @Valid @RequestBody PinCompanyNoteRequest request,
                                   HttpServletRequest httpRequest) {
        return notes.pin(principal.userId(), principal.requireWorkspaceId(), projectId, triageCompanyId, noteId,
                request.pinned(), httpRequest);
    }
}
