package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.outreach.dto.ScheduledMessageResponse;
import app.lightmove.api.outreach.dto.SendMessageRequest;
import app.lightmove.api.outreach.service.OutreachMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A consultant's own email to an executive on a position, sent from their mailbox. {@code WORK_EXECUTE}. */
@RestController
@RequiredArgsConstructor
public class OutreachMessageController {

    private final OutreachMessageService messages;

    @PostMapping("/api/v1/projects/{projectId}/outreach/conversations/{candidateId}/messages")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledMessageResponse send(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                         @Valid @RequestBody SendMessageRequest request,
                                         HttpServletRequest httpRequest) {
        return messages.schedule(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request,
                httpRequest);
    }

    @DeleteMapping("/api/v1/projects/{projectId}/outreach/messages/{messageId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID messageId, HttpServletRequest httpRequest) {
        messages.cancel(principal.userId(), principal.requireWorkspaceId(), projectId, messageId, httpRequest);
    }
}
