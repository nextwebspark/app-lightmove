package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether a Google ID token is one of our own service accounts' (#956): the meeting bots' for the run token exchange,
 * the canary's — a different account — for its reports.
 */
@Component
@RequiredArgsConstructor
class NotetakerBotIdentity {

    private final BotIdentityVerifier verifier;
    private final LightMoveProperties properties;

    boolean isOurBot(String idToken) {
        return isOf(properties.notetaker().botServiceAccount(), idToken);
    }

    boolean isOurCanary(String idToken) {
        return isOf(properties.notetaker().canaryServiceAccount(), idToken);
    }

    private boolean isOf(String expected, String idToken) {
        return expected != null && !expected.isBlank() && idToken != null && !idToken.isBlank()
                && verifier.serviceAccountOf(idToken.strip()).filter(expected.strip()::equalsIgnoreCase).isPresent();
    }
}
