package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.outreach.dto.BookMeetingRequest;
import app.lightmove.api.outreach.dto.MeetingSlotsResponse;
import app.lightmove.api.outreach.dto.PersonMeetingsResponse;
import app.lightmove.api.outreach.service.MeetingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * An executive's meetings on the team's calendars, and Book a call. {@code WORK_EXECUTE}: a client seat
 * sees none of a firm's diaries and books nothing.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach/candidates/{candidateId}/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetings;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonMeetingsResponse list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                       @PathVariable UUID candidateId) {
        return meetings.ofCandidate(principal.requireWorkspaceId(), projectId, candidateId);
    }

    @GetMapping("/slots")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public MeetingSlotsResponse slots(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                      @PathVariable UUID candidateId, @RequestParam(defaultValue = "30") int minutes) {
        return meetings.slots(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, minutes);
    }

    @PostMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void book(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                     @PathVariable UUID candidateId, @Valid @RequestBody BookMeetingRequest request,
                     HttpServletRequest httpRequest) {
        meetings.book(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request, httpRequest);
    }
}
