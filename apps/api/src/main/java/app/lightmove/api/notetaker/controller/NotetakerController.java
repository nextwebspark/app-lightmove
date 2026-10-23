package app.lightmove.api.notetaker.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.notetaker.dto.ApplyCallSuggestionRequest;
import app.lightmove.api.notetaker.dto.CallSuggestionsResponse;
import app.lightmove.api.notetaker.dto.CandidateNotetakerResponse;
import app.lightmove.api.notetaker.dto.NotetakerRecordingResponse;
import app.lightmove.api.notetaker.dto.NotetakerTranscriptResponse;
import app.lightmove.api.notetaker.dto.ScheduleNotetakerRequest;
import app.lightmove.api.notetaker.service.CallSuggestionService;
import app.lightmove.api.notetaker.service.NotetakerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Notetaker from the executive drawer: send a bot to a call, call it back, try a failed note again, read what it
 * heard, and review the changes it suggests for the person's record. {@code WORK_EXECUTE}: a client seat sees none of a firm's calls, and sends nothing into one.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach/candidates/{candidateId}")
@RequiredArgsConstructor
public class NotetakerController {

    private final NotetakerService notetaker;
    private final CallSuggestionService suggestions;

    @GetMapping("/notetaker")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidateNotetakerResponse list(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        return notetaker.ofCandidate(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId);
    }

    @PostMapping("/meetings/{meetingId}/notetaker")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public NotetakerRecordingResponse schedule(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                               @PathVariable UUID meetingId,
                                               @RequestBody(required = false) ScheduleNotetakerRequest request,
                                               HttpServletRequest httpRequest) {
        return notetaker.schedule(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                meetingId, request != null && request.liveCopilot(), httpRequest);
    }

    @DeleteMapping("/notetaker/{recordingId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID candidateId, @PathVariable UUID recordingId,
                       HttpServletRequest httpRequest) {
        notetaker.cancel(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, recordingId,
                httpRequest);
    }

    @PostMapping("/notetaker/{recordingId}/retry")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public NotetakerRecordingResponse retry(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                            @PathVariable UUID recordingId) {
        return notetaker.retry(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId);
    }

    @GetMapping("/notetaker/{recordingId}/transcript")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public NotetakerTranscriptResponse transcript(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                                  @PathVariable UUID recordingId) {
        return notetaker.transcript(principal.requireWorkspaceId(), projectId, candidateId, recordingId);
    }

    @GetMapping("/notetaker/{recordingId}/suggestions")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CallSuggestionsResponse suggestions(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                               @PathVariable UUID recordingId) {
        return suggestions.read(principal.requireWorkspaceId(), projectId, candidateId, recordingId);
    }

    /** Spends a model call, so it is a press: the drawer never asks on its own. */
    @PostMapping("/notetaker/{recordingId}/suggestions")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CallSuggestionsResponse suggest(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                           @PathVariable UUID recordingId, HttpServletRequest httpRequest) {
        return suggestions.ask(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId, httpRequest);
    }

    @PostMapping("/notetaker/{recordingId}/suggestions/{suggestionId}/apply")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CallSuggestionsResponse apply(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                         @PathVariable UUID recordingId, @PathVariable String suggestionId,
                                         @Valid @RequestBody(required = false) ApplyCallSuggestionRequest request,
                                         HttpServletRequest httpRequest) {
        return suggestions.apply(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId, suggestionId, request, httpRequest);
    }

    @PostMapping("/notetaker/{recordingId}/suggestions/{suggestionId}/skip")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CallSuggestionsResponse skip(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                        @PathVariable UUID recordingId, @PathVariable String suggestionId,
                                        HttpServletRequest httpRequest) {
        return suggestions.skip(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId, suggestionId, httpRequest);
    }

    /** Undo from the review's summary: a skipped change waits again, an applied one is put back first. */
    @PostMapping("/notetaker/{recordingId}/suggestions/{suggestionId}/reopen")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CallSuggestionsResponse reopen(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                          @PathVariable UUID recordingId, @PathVariable String suggestionId,
                                          HttpServletRequest httpRequest) {
        return suggestions.reopen(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId, suggestionId, httpRequest);
    }
}
