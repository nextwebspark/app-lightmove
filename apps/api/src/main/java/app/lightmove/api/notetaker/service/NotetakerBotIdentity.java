package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Whether a Google ID token is our bot's own service account's (#956): the run token exchange's and the canary's. */
@Component
@RequiredArgsConstructor
class NotetakerBotIdentity {

    private final BotIdentityVerifier verifier;
    private final LightMoveProperties properties;

    boolean isOurBot(String idToken) {
        String expected = properties.notetaker().botServiceAccount();
        return expected != null && !expected.isBlank() && idToken != null && !idToken.isBlank()
                && verifier.serviceAccountOf(idToken.strip()).filter(expected.strip()::equalsIgnoreCase).isPresent();
    }
}
