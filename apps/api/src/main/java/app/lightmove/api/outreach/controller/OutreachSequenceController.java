package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.outreach.dto.DraftOpenersRequest;
import app.lightmove.api.outreach.dto.DraftOpenersResponse;
import app.lightmove.api.outreach.dto.DraftedOpenerResponse;
import app.lightmove.api.outreach.dto.EnrollmentCandidatesRequest;
import app.lightmove.api.outreach.dto.EnrollmentCandidatesResponse;
import app.lightmove.api.outreach.dto.SaveSequenceRequest;
import app.lightmove.api.outreach.dto.SequenceResponse;
import app.lightmove.api.outreach.dto.SequencesResponse;
import app.lightmove.api.outreach.dto.StartSequenceRequest;
import app.lightmove.api.outreach.dto.StartSequenceResponse;
import app.lightmove.api.outreach.service.OutreachEnrollmentService;
import app.lightmove.api.outreach.service.OutreachOpenerService;
import app.lightmove.api.outreach.service.OutreachSequenceService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A position's outreach: its sequences, the Add to sequence dialog's reads and the press that starts
 * one. All {@code WORK_EXECUTE} — outreach is the firm's work on the mandate, and a client seat sees none of it.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach")
@RequiredArgsConstructor
public class OutreachSequenceController {

    private final OutreachSequenceService sequences;
    private final OutreachEnrollmentService enrollments;
    private final OutreachOpenerService openers;

    @GetMapping("/sequences")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SequencesResponse list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId) {
        return sequences.list(principal.requireWorkspaceId(), projectId);
    }

    @PostMapping("/sequences")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public SequenceResponse create(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                   @Valid @RequestBody SaveSequenceRequest request, HttpServletRequest httpRequest) {
        return sequences.create(principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @GetMapping("/sequences/{sequenceId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SequenceResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                @PathVariable UUID sequenceId) {
        return sequences.get(principal.requireWorkspaceId(), projectId, sequenceId);
    }

    @PutMapping("/sequences/{sequenceId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SequenceResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                   @PathVariable UUID sequenceId, @Valid @RequestBody SaveSequenceRequest request,
                                   HttpServletRequest httpRequest) {
        return sequences.update(principal.userId(), principal.requireWorkspaceId(), projectId, sequenceId, request,
                httpRequest);
    }

    @DeleteMapping("/sequences/{sequenceId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID sequenceId, HttpServletRequest httpRequest) {
        sequences.delete(principal.userId(), principal.requireWorkspaceId(), projectId, sequenceId, httpRequest);
    }

    /** A read with a body: the companies ticked on a stage can run past what a query string should carry. */
    @PostMapping("/enrollment-candidates")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public EnrollmentCandidatesResponse candidates(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId,
                                                   @Valid @RequestBody EnrollmentCandidatesRequest request) {
        return enrollments.candidates(principal.userId(), principal.requireWorkspaceId(), projectId, request);
    }

    @PostMapping("/openers")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public DraftOpenersResponse draftOpeners(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId,
                                             @Valid @RequestBody DraftOpenersRequest request,
                                             HttpServletRequest httpRequest) {
        return new DraftOpenersResponse(openers.draft(principal.userId(), principal.requireWorkspaceId(), projectId,
                        request.candidateIds(), httpRequest).stream()
                .map(DraftedOpenerResponse::of)
                .toList());
    }

    @PostMapping("/sequences/{sequenceId}/enrollments")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public StartSequenceResponse start(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                       @PathVariable UUID sequenceId, @Valid @RequestBody StartSequenceRequest request,
                                       HttpServletRequest httpRequest) {
        return enrollments.start(principal.userId(), principal.requireWorkspaceId(), projectId, sequenceId, request,
                httpRequest);
    }
}
