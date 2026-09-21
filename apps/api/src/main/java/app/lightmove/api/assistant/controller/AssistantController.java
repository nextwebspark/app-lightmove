package app.lightmove.api.assistant.controller;

import app.lightmove.api.assistant.dto.AskRequest;
import app.lightmove.api.assistant.dto.AssistantThreadResponse;
import app.lightmove.api.assistant.dto.AssistantThreadSummary;
import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.service.AssistantThreadService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own assistant conversations.
 *
 * <p>Gated on {@code member(principal)} rather than a named action, and that is the deliberate part.
 * It is the one guard that admits a <b>pure client</b>, because a client representative should be
 * able to ask about the mandates they are attached to. What they can then reach is not decided here:
 * a client holds no {@code PROJECT_BROWSE}, so once tools exist the market-side ones refuse them at
 * the tool while the report and mandate ones do not. The capability follows from the action model
 * rather than from a branch on role.
 *
 * <p>The guard proves membership and nothing more — the service does the scoping, on
 * {@code (workspaceId, userId)}. {@code ProjectsController.list} sets that division.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantThreadService assistant;

    @GetMapping("/threads")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public ResponseEntity<List<AssistantThreadSummary>> threads(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(assistant.list(principal.userId(), principal.requireWorkspaceId()));
    }

    @GetMapping("/threads/{threadId}")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public ResponseEntity<AssistantThreadResponse> thread(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable UUID threadId) {
        return ResponseEntity.ok(
                assistant.get(threadId, principal.userId(), principal.requireWorkspaceId()));
    }

    /**
     * Starts a conversation, titled from the question.
     *
     * <p><b>202, not 201.</b> The body is the accepted turn — RUNNING, with no answer yet — and the
     * answer arrives on the stream the {@code Location} header names, or on a refetch of the thread.
     * A turn with tools runs 30–180s, which no single response can hold against a 55s stream cycle.
     */
    @PostMapping("/ask")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public ResponseEntity<AssistantTurnResponse> ask(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @Valid @RequestBody AskRequest request,
                                                     HttpServletRequest httpRequest) {
        AssistantTurnResponse accepted = assistant.ask(
                principal.userId(), principal.requireWorkspaceId(), null, request, httpRequest);
        return accepted(accepted);
    }

    /** Continues one, also 202. A thread that is not the caller's answers 404, never 403. */
    @PostMapping("/threads/{threadId}/ask")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public ResponseEntity<AssistantTurnResponse> askIn(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID threadId,
                                                       @Valid @RequestBody AskRequest request,
                                                       HttpServletRequest httpRequest) {
        AssistantTurnResponse accepted = assistant.ask(
                principal.userId(), principal.requireWorkspaceId(), threadId, request, httpRequest);
        return accepted(accepted);
    }

    /** Where to watch it happen. The one part of a 202 that tells a client what to do next. */
    private static ResponseEntity<AssistantTurnResponse> accepted(AssistantTurnResponse turn) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(HttpHeaders.LOCATION, "/api/v1/assistant/turns/" + turn.id() + "/stream")
                .body(turn);
    }
}
