package app.lightmove.api.core.security.model;

import java.time.Duration;

/** A correct password from a person with a factor: the challenge's plaintext, for the cookie alone. */
public record MfaChallengeIssued(String challengeToken, Duration expiresIn) implements SignInOutcome {

    @Override
    public String toString() {
        return "MfaChallengeIssued[challengeToken=<redacted>, expiresIn=" + expiresIn + "]";
    }
}
