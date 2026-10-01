package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.PersonTimelineResponse;
import app.lightmove.api.candidate.dto.PinPersonNoteRequest;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.service.PersonNoteService;
import app.lightmove.api.candidate.service.PersonRecordService;
import app.lightmove.api.candidate.service.PersonTimelineService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
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
 * The workspace's people outside any one mandate (decision D2): a researcher seated on nothing still
 * reads the firm's people. {@code CANDIDATE_POOL_MANAGE}, held by ADMIN and MEMBER and never CLIENT. The
 * routes keep the user-facing word, Candidates; the ids they take are person ids.
 */
@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
public class CandidatePoolController {

    private final PersonRecordService records;
    private final PersonNoteService notes;
    private final PersonTimelineService timeline;

    /** Everything recorded across the workspace's people, newest first; each filter is optional. */
    @GetMapping("/activity")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonTimelineResponse activity(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(required = false) UUID actor,
                                           @RequestParam(required = false) UUID position,
                                           @RequestParam(required = false) String group,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to,
                                           @RequestParam(required = false) Long before,
                                           @RequestParam(required = false) Integer limit) {
        return timeline.feedOf(principal.requireWorkspaceId(), actor, position, group, from, to, before, limit);
    }

    @GetMapping("/{personId}")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId) {
        return records.recordOf(principal.requireWorkspaceId(), personId);
    }

    @GetMapping("/{personId}/notes")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public List<PersonNoteResponse> notes(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID personId) {
        return notes.list(principal.userId(), principal.requireWorkspaceId(), personId);
    }

    @PostMapping("/{personId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonNoteResponse write(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                    @Valid @RequestBody WritePersonNoteRequest request,
                                    HttpServletRequest httpRequest) {
        return notes.write(principal.userId(), principal.requireWorkspaceId(), personId, request.projectId(),
                request, httpRequest);
    }

    @PutMapping("/{personId}/notes/{noteId}")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonNoteResponse revise(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                     @PathVariable UUID noteId, @Valid @RequestBody WritePersonNoteRequest request,
                                     HttpServletRequest httpRequest) {
        return notes.revise(principal.userId(), principal.requireWorkspaceId(), personId, noteId, request,
                httpRequest);
    }

    @DeleteMapping("/{personId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                       @PathVariable UUID noteId, HttpServletRequest httpRequest) {
        notes.remove(principal.userId(), principal.requireWorkspaceId(), personId, noteId, httpRequest);
    }

    @PatchMapping("/{personId}/notes/{noteId}/pin")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonNoteResponse pin(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                  @PathVariable UUID noteId, @Valid @RequestBody PinPersonNoteRequest request,
                                  HttpServletRequest httpRequest) {
        return notes.pin(principal.userId(), principal.requireWorkspaceId(), personId, noteId, request.pinned(),
                httpRequest);
    }

    @GetMapping("/{personId}/timeline")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonTimelineResponse timeline(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID personId,
                                           @RequestParam(required = false) String group,
                                           @RequestParam(required = false) Long before,
                                           @RequestParam(required = false) Integer limit) {
        return timeline.timelineOf(principal.requireWorkspaceId(), personId, group, before, limit);
    }
}
