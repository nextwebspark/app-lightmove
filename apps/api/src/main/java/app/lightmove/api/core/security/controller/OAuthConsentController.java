package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.OAuthConsentContextResponse;
import app.lightmove.api.core.security.dto.OAuthPendingConsentResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.oauth.OAuthConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The consent screen's reads, on the session's own token. {@code /consent} is also where the authorization server
 * sends a stored request for its consent (its {@code consentPage}), which the screen's fetch follows.
 */
@RestController
@RequestMapping("/api/v1/oauth")
@RequiredArgsConstructor
public class OAuthConsentController {

    private final OAuthConsentService consents;

    @GetMapping("/consent-context")
    public OAuthConsentContextResponse context(@AuthenticationPrincipal AuthPrincipal principal,
                                               @RequestParam("client_id") String clientId,
                                               @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                               @RequestParam(required = false) String scope) {
        return consents.context(principal.userId(), clientId, redirectUri, scope);
    }

    @GetMapping("/consent")
    public OAuthPendingConsentResponse pending(@AuthenticationPrincipal AuthPrincipal principal,
                                               @RequestParam("client_id") String clientId,
                                               @RequestParam String state) {
        return consents.pending(principal.userId(), clientId, state);
    }
}
