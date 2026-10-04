package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.dto.MailboxConnectResponse;
import app.lightmove.api.outreach.dto.ZoomResponse;
import app.lightmove.api.outreach.model.MailboxConnectStart;
import app.lightmove.api.outreach.service.ZoomService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A consultant's own Zoom account, for the link Book a call puts on an invite. Staff only, and every route reads the
 * caller's own row. Connecting works as a mailbox's does: a popup, a public callback, and a cookie binding the
 * state to the browser that started it.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ZoomController {

    static final String CONNECT_COOKIE = "lm_zoom_connect";

    /** Zoom's own answer when someone presses Cancel (RFC 6749 §4.1.2.1). */
    private static final String PROVIDER_CANCELLED = "access_denied";

    private final ZoomService zoom;
    private final LightMoveProperties properties;

    @GetMapping("/api/v1/outreach/zoom")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public ZoomResponse mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return zoom.view(principal.userId(), principal.requireWorkspaceId());
    }

    @PostMapping("/api/v1/outreach/zoom/connect")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public ResponseEntity<MailboxConnectResponse> connect(@AuthenticationPrincipal AuthPrincipal principal) {
        MailboxConnectStart start = zoom.begin(principal.userId(), principal.requireWorkspaceId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, connectCookie(start.state(),
                        properties.outreach().connectWindow().toSeconds()).toString())
                .body(new MailboxConnectResponse(start.authorizationUri().toString()));
    }

    /** Public, because a navigation carries no bearer token. It always answers with a redirect to the SPA. */
    @GetMapping(ZoomService.CALLBACK_PATH)
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String error,
                                         @CookieValue(name = CONNECT_COOKIE, required = false) String browserState,
                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(landingFor(state, code, error, browserState, request))
                .header(HttpHeaders.SET_COOKIE, connectCookie("", 0).toString())
                .build();
    }

    @DeleteMapping("/api/v1/outreach/zoom")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
        zoom.disconnect(principal.userId(), principal.requireWorkspaceId(), request);
    }

    private URI landingFor(String state, String code, String error, String browserState, HttpServletRequest request) {
        UriComponentsBuilder landing = UriComponentsBuilder.fromUri(zoom.landingUri());
        if (error != null) {
            ErrorCode refusal = PROVIDER_CANCELLED.equals(error)
                    ? ErrorCode.ZOOM_CONNECT_CANCELLED : ErrorCode.ZOOM_CONNECT_FAILED;
            log.info("Zoom consent screen answered {}", error);
            return landing.queryParam("error", refusal.name()).build().toUri();
        }
        try {
            zoom.complete(state, browserState, code, request);
            return landing.queryParam("status", "connected").build().toUri();
        } catch (ApiException refused) {
            return landing.queryParam("error", refused.getCode().name()).build().toUri();
        } catch (Exception unexpected) {
            log.error("Zoom connection callback failed", unexpected);
            return landing.queryParam("error", ErrorCode.ZOOM_CONNECT_FAILED.name()).build().toUri();
        }
    }

    /** {@code SameSite=Lax}: the callback is a top-level navigation Zoom starts from its own site. */
    private ResponseCookie connectCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(CONNECT_COOKIE, value)
                .httpOnly(true)
                .secure(properties.auth().cookie().secure())
                .sameSite("Lax")
                .path(ZoomService.CALLBACK_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
