package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.dto.MailboxConnectRequest;
import app.lightmove.api.outreach.dto.MailboxConnectResponse;
import app.lightmove.api.outreach.dto.MailboxResponse;
import app.lightmove.api.outreach.dto.MailboxTimeZoneRequest;
import app.lightmove.api.outreach.model.MailboxConnectStart;
import app.lightmove.api.outreach.service.MailboxService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A consultant's own mailbox for outreach. Staff only: a client representative sends nothing. Each
 * route reads the caller's own row, so no id ever names someone else's mailbox.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MailboxController {

    /** Holds the raw state for the browser that started a connection; see {@link MailboxService#complete}. */
    static final String CONNECT_COOKIE = "lm_mailbox_connect";

    /** The provider's own answer when someone presses Cancel (RFC 6749 §4.1.2.1). */
    private static final String PROVIDER_CANCELLED = "access_denied";

    private final MailboxService mailboxes;
    private final LightMoveProperties properties;

    @GetMapping("/api/v1/outreach/mailbox")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public MailboxResponse mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return mailboxes.view(principal.userId(), principal.requireWorkspaceId());
    }

    @PostMapping("/api/v1/outreach/mailbox/connect")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public ResponseEntity<MailboxConnectResponse> connect(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @Valid @RequestBody MailboxConnectRequest body) {
        MailboxConnectStart start = mailboxes.begin(principal.userId(), principal.requireWorkspaceId(),
                principal.email(), body.provider());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, connectCookie(start.state(),
                        properties.outreach().connectWindow().toSeconds()).toString())
                .body(new MailboxConnectResponse(start.authorizationUri().toString()));
    }

    /**
     * Where the provider's consent screen sends the browser back. Public, because a navigation carries no
     * bearer token; the state and the cookie beside it are the credential. It always answers with a
     * redirect to the SPA — a problem document here would be a raw JSON page inside the connect popup.
     */
    @GetMapping(MailboxService.CALLBACK_PATH)
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String error,
                                         @CookieValue(name = CONNECT_COOKIE, required = false) String browserState,
                                         HttpServletRequest request) {
        URI landing = landingFor(state, code, error, browserState, request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(landing)
                .header(HttpHeaders.SET_COOKIE, connectCookie("", 0).toString())
                .build();
    }

    @PutMapping("/api/v1/outreach/mailbox/time-zone")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public MailboxResponse changeTimeZone(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody MailboxTimeZoneRequest body, HttpServletRequest request) {
        return mailboxes.changeTimeZone(principal.userId(), principal.requireWorkspaceId(), body.timeZone(), request);
    }

    @DeleteMapping("/api/v1/outreach/mailbox")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
        mailboxes.disconnect(principal.userId(), principal.requireWorkspaceId(), request);
    }

    @PostMapping("/api/v1/outreach/mailbox/test")
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendTest(@AuthenticationPrincipal AuthPrincipal principal) {
        mailboxes.sendTest(principal.userId(), principal.requireWorkspaceId());
    }

    private URI landingFor(String state, String code, String error, String browserState, HttpServletRequest request) {
        UriComponentsBuilder landing = UriComponentsBuilder.fromUri(mailboxes.landingUri());
        if (error != null) {
            ErrorCode refusal = PROVIDER_CANCELLED.equals(error)
                    ? ErrorCode.MAILBOX_CONNECT_CANCELLED : ErrorCode.MAILBOX_CONNECT_FAILED;
            log.info("Mailbox consent screen answered {}", error);
            return landing.queryParam("error", refusal.name()).build().toUri();
        }
        try {
            mailboxes.complete(state, browserState, code, request);
            return landing.queryParam("status", "connected").build().toUri();
        } catch (ApiException refused) {
            return landing.queryParam("error", refused.getCode().name()).build().toUri();
        } catch (Exception unexpected) {
            log.error("Mailbox connection callback failed", unexpected);
            return landing.queryParam("error", ErrorCode.MAILBOX_CONNECT_FAILED.name()).build().toUri();
        }
    }

    /**
     * {@code SameSite=Lax}, never {@code Strict}: the callback is a top-level navigation the provider
     * starts from its own site, and {@code Strict} withholds the cookie on exactly that request.
     */
    private ResponseCookie connectCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(CONNECT_COOKIE, value)
                .httpOnly(true)
                .secure(properties.auth().cookie().secure())
                .sameSite("Lax")
                .path(MailboxService.CALLBACK_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
