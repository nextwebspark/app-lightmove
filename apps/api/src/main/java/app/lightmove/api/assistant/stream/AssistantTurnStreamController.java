package app.lightmove.api.assistant.stream;

import app.lightmove.api.core.security.model.AuthPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live half of one assistant turn: everything it has said after {@code afterSeq}, then whatever
 * it says next.
 *
 * <p>Gated on {@code member(principal)} to match {@code AssistantController} exactly — the one guard
 * that admits a pure client, so a client representative can watch their own question being answered.
 * The guard proves membership and nothing more; the service does the scoping on
 * {@code (workspaceId, actorUserId)}.
 *
 * <p>Deliberately <b>not</b> a reuse of {@code ProjectStreamController}: its
 * {@code @projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')} is a project-seat gate, and a
 * turn has no seat — its thread's {@code projectId} is context and is nullable.
 *
 * <p>{@code afterSeq} is the client's cursor, and 0 replays the turn from the beginning. A reconnect
 * sends the last seq it actually received, which is what turns the server's scheduled ~55s close
 * into an invisible seam rather than a gap.
 */
@RestController
@RequestMapping("/api/v1/assistant/turns/{turnId}/stream")
@RequiredArgsConstructor
public class AssistantTurnStreamController {

    private final AssistantTurnStreamService streams;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public SseEmitter stream(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable UUID turnId,
                             @RequestParam(defaultValue = "0") int afterSeq) {
        return streams.stream(turnId, principal.userId(), principal.requireWorkspaceId(), afterSeq);
    }
}
