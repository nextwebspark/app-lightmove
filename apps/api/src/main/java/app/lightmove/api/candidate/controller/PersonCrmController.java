package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.PersonPositionResponse;
import app.lightmove.api.candidate.dto.PersonTimelineResponse;
import app.lightmove.api.candidate.dto.PinPersonNoteRequest;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.service.PersonNoteService;
import app.lightmove.api.candidate.service.PersonRecordService;
import app.lightmove.api.candidate.service.PersonTimelineService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
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

/**
 * The shared person behind one of a mandate's rows — where else they are mapped, the notes on them, and
 * their history — reached from the position's own drawer. WORK_EXECUTE throughout, so a researcher seated
 * on the mandate needs no workspace action and a client seat reaches none of it (decision D1).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}")
@RequiredArgsConstructor
public class PersonCrmController {

    private final PersonRecordService records;
    private final PersonNoteService notes;
    private final PersonTimelineService timeline;

    @GetMapping("/positions")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public List<PersonPositionResponse> positions(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        UUID workspaceId = principal.requireWorkspaceId();
        return records.positionsOf(workspaceId, records.personOf(workspaceId, projectId, candidateId), projectId);
    }

    @GetMapping("/notes")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public List<PersonNoteResponse> notes(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        UUID workspaceId = principal.requireWorkspaceId();
        return notes.list(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId));
    }

    /** A note written here is about this position; the request's own {@code projectId} is not read. */
    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonNoteResponse write(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                    @Valid @RequestBody WritePersonNoteRequest request,
                                    HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        return notes.write(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId),
                projectId, request, httpRequest);
    }

    @PutMapping("/notes/{noteId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonNoteResponse revise(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                     @PathVariable UUID noteId, @Valid @RequestBody WritePersonNoteRequest request,
                                     HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        return notes.revise(principal.userId(), workspaceId,
                records.personOf(workspaceId, projectId, candidateId), noteId, request, httpRequest);
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable UUID projectId, @PathVariable UUID candidateId, @PathVariable UUID noteId,
                       HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        notes.remove(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId), noteId,
                httpRequest);
    }

    @PatchMapping("/notes/{noteId}/pin")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonNoteResponse pin(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                  @PathVariable UUID noteId, @RequestBody PinPersonNoteRequest request) {
        UUID workspaceId = principal.requireWorkspaceId();
        return notes.pin(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId),
                noteId, request.pinned());
    }

    @GetMapping("/timeline")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonTimelineResponse timeline(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                           @RequestParam(required = false) String group,
                                           @RequestParam(required = false) Long before,
                                           @RequestParam(required = false) Integer limit) {
        UUID workspaceId = principal.requireWorkspaceId();
        return timeline.timelineOf(workspaceId, records.personOf(workspaceId, projectId, candidateId), group,
                before, limit);
    }
}
