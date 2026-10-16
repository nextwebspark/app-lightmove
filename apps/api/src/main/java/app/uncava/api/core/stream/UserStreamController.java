package app.uncava.api.core.stream;

import app.uncava.api.core.security.model.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live half of the bell: a staff member's own stream, telling the tab to read their notifications again.
 * It carries no content — the rows come back through the guarded read. A pure client has no bell.
 */
@RestController
@RequiredArgsConstructor
public class UserStreamController {

    private final UserStreamRegistry registry;

    @GetMapping(value = "/api/v1/me/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public SseEmitter stream(@AuthenticationPrincipal AuthPrincipal principal) {
        return registry.subscribe(principal.userId(), principal.requireWorkspaceId());
    }
}
