package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.ActiveSessionResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.token.ActiveSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Security's session list, under {@code /auth} because only there is the refresh cookie
 * sent. No {@code @PreAuthorize}: the user is the principal, never the path.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
public class ActiveSessionController {

    private final ActiveSessionService sessions;

    @GetMapping
    public List<ActiveSessionResponse> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken) {

        return sessions.list(principal.userId(), refreshToken);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID sessionId,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        sessions.revoke(principal.userId(), sessionId, refreshToken, httpRequest);
    }

    @PostMapping("/revoke-others")
    public RevokedSessions revokeOthers(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        return new RevokedSessions(
                sessions.revokeOthers(principal.userId(), refreshToken, httpRequest));
    }

    public record RevokedSessions(int revoked) {}
}
