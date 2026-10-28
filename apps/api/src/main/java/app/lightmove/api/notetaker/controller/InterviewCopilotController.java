package app.lightmove.api.notetaker.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.notetaker.dto.CopilotResponse;
import app.lightmove.api.notetaker.dto.InterviewPlanResponse;
import app.lightmove.api.notetaker.dto.LiveCopilotRequest;
import app.lightmove.api.notetaker.dto.NotetakerRecordingResponse;
import app.lightmove.api.notetaker.dto.PlanQuestionAskedRequest;
import app.lightmove.api.notetaker.dto.SaveInterviewPlanRequest;
import app.lightmove.api.notetaker.service.InterviewCopilotService;
import app.lightmove.api.notetaker.service.InterviewPlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The interview copilot from the executive drawer: the questions prepared for a call, and the live copilot that follows
 * it. {@code WORK_EXECUTE}: a client seat never sees how a firm interviews.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach/candidates/{candidateId}")
@RequiredArgsConstructor
public class InterviewCopilotController {

    private final InterviewPlanService plans;
    private final InterviewCopilotService copilot;

    @GetMapping("/meetings/{meetingId}/plan")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public InterviewPlanResponse plan(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                      @PathVariable UUID candidateId, @PathVariable UUID meetingId) {
        return plans.read(principal.requireWorkspaceId(), projectId, candidateId, meetingId);
    }

    /** Spends a model call, so it is a press: the dialog asks on opening only when no plan was drafted yet. */
    @PostMapping("/meetings/{meetingId}/plan")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public InterviewPlanResponse draftPlan(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                           @PathVariable UUID meetingId, HttpServletRequest httpRequest) {
        return plans.draft(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, meetingId,
                httpRequest);
    }

    @PutMapping("/meetings/{meetingId}/plan")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public InterviewPlanResponse savePlan(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                          @PathVariable UUID meetingId,
                                          @Valid @RequestBody SaveInterviewPlanRequest request,
                                          HttpServletRequest httpRequest) {
        return plans.save(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, meetingId,
                request, httpRequest);
    }

    /** Before the Notetaker joins only: Recall streams a transcript to a bot made for it, so the bot is changed too. */
    @PutMapping("/notetaker/{recordingId}/copilot")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public NotetakerRecordingResponse switchLive(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                                 @PathVariable UUID recordingId,
                                                 @RequestBody LiveCopilotRequest request,
                                                 HttpServletRequest httpRequest) {
        return copilot.switchLive(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId, request.enabled(), httpRequest);
    }

    @GetMapping("/notetaker/{recordingId}/copilot")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CopilotResponse copilot(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                   @PathVariable UUID candidateId, @PathVariable UUID recordingId,
                                   @RequestParam(defaultValue = "0") long after) {
        return copilot.read(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, recordingId,
                after);
    }

    /** Tells the window when to read again; it carries no content. */
    @GetMapping(path = "/notetaker/{recordingId}/copilot/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SseEmitter stream(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                             @PathVariable UUID candidateId, @PathVariable UUID recordingId,
                             HttpServletRequest httpRequest) {
        return copilot.subscribe(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                recordingId, httpRequest);
    }

    @PostMapping("/notetaker/{recordingId}/copilot/stop")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CopilotResponse stop(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                @PathVariable UUID candidateId, @PathVariable UUID recordingId,
                                HttpServletRequest httpRequest) {
        return copilot.stop(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, recordingId,
                httpRequest);
    }

    @PutMapping("/notetaker/{recordingId}/copilot/plan/{questionId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsked(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                          @PathVariable UUID candidateId, @PathVariable UUID recordingId,
                          @PathVariable String questionId, @RequestBody PlanQuestionAskedRequest request) {
        copilot.markAsked(principal.requireWorkspaceId(), projectId, candidateId, recordingId, questionId,
                request.asked());
    }
}
