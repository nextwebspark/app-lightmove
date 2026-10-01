package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.AssignPersonOwnerRequest;
import app.lightmove.api.candidate.dto.BulkAssignOwnerRequest;
import app.lightmove.api.candidate.dto.BulkPeopleChangeResponse;
import app.lightmove.api.candidate.dto.BulkTagPeopleRequest;
import app.lightmove.api.candidate.dto.CandidatePoolResponse;
import app.lightmove.api.candidate.dto.CandidatePoolSizeResponse;
import app.lightmove.api.candidate.dto.DoNotContactRequest;
import app.lightmove.api.candidate.dto.MapPeopleToPositionRequest;
import app.lightmove.api.candidate.dto.MapPeopleToPositionResponse;
import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.PersonTimelineResponse;
import app.lightmove.api.candidate.dto.PinPersonNoteRequest;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.candidate.model.StoredPhoto;
import app.lightmove.api.candidate.service.CandidatePoolService;
import app.lightmove.api.candidate.service.PersonNoteService;
import app.lightmove.api.candidate.service.PersonRecordService;
import app.lightmove.api.candidate.service.PersonTimelineService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final CandidatePoolService pool;

    /**
     * A page of the workspace's people. Every filter is optional; {@code owner} is a user id or
     * {@code nobody}, and {@code tag} repeats.
     */
    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public CandidatePoolResponse list(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String view,
                                      @RequestParam(required = false) List<UUID> tag,
                                      @RequestParam(required = false) String tagMatch,
                                      @RequestParam(required = false) UUID position,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String owner,
                                      @RequestParam(required = false) String country,
                                      @RequestParam(required = false) String sort,
                                      @RequestParam(required = false) String direction,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        PoolCriteria criteria = PoolCriteria.read(q, view, tag, tagMatch, position, status, owner, country, sort,
                direction);
        return pool.list(principal.userId(), principal.requireWorkspaceId(), criteria, page, size);
    }

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

    /** The workspace's people, counted — the nav's badge. */
    @GetMapping("/count")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public CandidatePoolSizeResponse count(@AuthenticationPrincipal AuthPrincipal principal) {
        return new CandidatePoolSizeResponse(pool.sizeOf(principal.requireWorkspaceId()));
    }

    @GetMapping("/{personId}")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId) {
        return records.recordOf(principal.userId(), principal.requireWorkspaceId(), personId);
    }

    /** The stored profile photo, inline — the position route's twin, for staff reading the pool. */
    @GetMapping("/{personId}/photo")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID personId) {
        StoredPhoto photo = records.photoOf(principal.requireWorkspaceId(), personId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(photo.content());
    }

    @PutMapping("/{personId}/owner")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse assignOwner(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID personId,
                                            @RequestBody AssignPersonOwnerRequest request,
                                            HttpServletRequest httpRequest) {
        return pool.assignOwner(principal.userId(), principal.requireWorkspaceId(), personId, request.ownerUserId(),
                httpRequest);
    }

    @PutMapping("/{personId}/do-not-contact")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse doNotContact(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID personId,
                                             @Valid @RequestBody DoNotContactRequest request,
                                             HttpServletRequest httpRequest) {
        return pool.markDoNotContact(principal.userId(), principal.requireWorkspaceId(), personId,
                request.doNotContact(), request.reason(), httpRequest);
    }

    @PutMapping("/{personId}/tags/{tagId}")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse tag(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                    @PathVariable UUID tagId, HttpServletRequest httpRequest) {
        return pool.tag(principal.userId(), principal.requireWorkspaceId(), personId, tagId, httpRequest);
    }

    @DeleteMapping("/{personId}/tags/{tagId}")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse untag(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                      @PathVariable UUID tagId, HttpServletRequest httpRequest) {
        return pool.untag(principal.userId(), principal.requireWorkspaceId(), personId, tagId, httpRequest);
    }

    @PostMapping("/bulk/tags")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public BulkPeopleChangeResponse retag(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody BulkTagPeopleRequest request,
                                          HttpServletRequest httpRequest) {
        return pool.retag(principal.userId(), principal.requireWorkspaceId(), request.personIds(), request.tagIds(),
                request.removes(), httpRequest);
    }

    @PostMapping("/bulk/owner")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public BulkPeopleChangeResponse assignOwners(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody BulkAssignOwnerRequest request,
                                                 HttpServletRequest httpRequest) {
        return pool.assignOwner(principal.userId(), principal.requireWorkspaceId(), request.personIds(),
                request.ownerUserId(), httpRequest);
    }

    /** Adds the people to a position as Identified; the caller needs that position's WORK_EXECUTE too. */
    @PostMapping("/bulk/position")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public MapPeopleToPositionResponse mapToPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @Valid @RequestBody MapPeopleToPositionRequest request,
                                                     HttpServletRequest httpRequest) {
        return pool.mapToPosition(principal.userId(), principal.requireWorkspaceId(), request.projectId(),
                request.personIds(), httpRequest);
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
