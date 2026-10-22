package app.lightmove.api.notetaker.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.notetaker.dto.ChangeNotetakerRequest;
import app.lightmove.api.notetaker.dto.NotetakerMeetingsResponse;
import app.lightmove.api.notetaker.service.NotetakerMeetingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Notetaker on an executive's meetings. {@code WORK_EXECUTE}, like the meetings themselves. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach/candidates/{candidateId}/meetings")
@RequiredArgsConstructor
public class NotetakerMeetingController {

    private final NotetakerMeetingService notetaker;

    @GetMapping("/notetaker")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public NotetakerMeetingsResponse list(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        return notetaker.of(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId);
    }

    @PutMapping("/{meetingId}/notetaker")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID candidateId, @PathVariable UUID meetingId,
                       @Valid @RequestBody ChangeNotetakerRequest request, HttpServletRequest httpRequest) {
        notetaker.setEnabled(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, meetingId,
                request.enabled(), httpRequest);
    }

    @PostMapping("/{meetingId}/notetaker/remove")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID candidateId, @PathVariable UUID meetingId,
                       HttpServletRequest httpRequest) {
        notetaker.remove(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, meetingId,
                httpRequest);
    }
}
