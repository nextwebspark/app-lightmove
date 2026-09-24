package app.lightmove.api.assistant.controller;

import app.lightmove.api.assistant.dto.AcceptProposalRequest;
import app.lightmove.api.assistant.dto.AskRequest;
import app.lightmove.api.assistant.dto.AssistantThreadResponse;
import app.lightmove.api.assistant.dto.AssistantThreadSummary;
import app.lightmove.api.assistant.service.AssistantAskStream;
import app.lightmove.api.assistant.service.AssistantProposalService;
import app.lightmove.api.assistant.service.AssistantService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The assistant inside one project. Asking needs {@code WORK_EXECUTE} on that project — the same
 * action filing the card needs — so the tools, which read the project from the request and never
 * from the model, need no check of their own. A chat that is not the caller's answers 404.
 */
@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistant;
    private final AssistantProposalService proposals;
    private final AssistantAskStream askStream;

    @GetMapping("/api/v1/projects/{projectId}/assistant/threads")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<List<AssistantThreadSummary>> threads(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId) {
        return ResponseEntity.ok(
                assistant.threads(principal.userId(), principal.requireWorkspaceId(), projectId));
    }

    /** Streams the steps as they happen, then the saved turn — see {@code AssistantAskStream}. */
    @PostMapping(path = "/api/v1/projects/{projectId}/assistant/ask",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public SseEmitter ask(@AuthenticationPrincipal AuthPrincipal principal,
                          @PathVariable UUID projectId,
                          @Valid @RequestBody AskRequest request) {
        return askStream.ask(principal.userId(), principal.requireWorkspaceId(), projectId,
                request.threadId(), request.question());
    }

    @GetMapping("/api/v1/assistant/threads/{threadId}")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public ResponseEntity<AssistantThreadResponse> thread(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable UUID threadId) {
        return ResponseEntity.ok(
                assistant.thread(threadId, principal.userId(), principal.requireWorkspaceId()));
    }

    /** {@code WORK_EXECUTE} is checked inside, against the project the stored chat belongs to. */
    @PostMapping("/api/v1/assistant/turns/{turnId}/accept")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public ResponseEntity<TriageBulkAddResponse> accept(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable UUID turnId,
                                                        @Valid @RequestBody AcceptProposalRequest request,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(proposals.accept(turnId, principal.userId(),
                principal.requireWorkspaceId(), request, httpRequest));
    }
}
