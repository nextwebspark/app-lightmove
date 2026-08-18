package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.ActiveSessionResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.token.ActiveSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Security's session list.
 *
 * <p>Mapped under {@code /auth} rather than under a settings path because the refresh cookie is scoped
 * to {@code /api/v1/auth}: nowhere else does the browser send the one thing that says which of these
 * sessions is the caller's.
 *
 * <p>No {@code @PreAuthorize}. Which user's sessions these are comes from the principal and never from
 * the path, so there is nothing to authorise — the same reasoning as {@code /auth/me}.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
public class ActiveSessionController {

    private final ActiveSessionService sessions;

    @GetMapping
    public ResponseEntity<List<ActiveSessionResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken) {

        return ResponseEntity.ok(sessions.list(principal.userId(), refreshToken));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID sessionId,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        sessions.revoke(principal.userId(), sessionId, refreshToken, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoke-others")
    public ResponseEntity<RevokedSessions> revokeOthers(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(new RevokedSessions(
                sessions.revokeOthers(principal.userId(), refreshToken, httpRequest)));
    }

    public record RevokedSessions(int revoked) {}
}
