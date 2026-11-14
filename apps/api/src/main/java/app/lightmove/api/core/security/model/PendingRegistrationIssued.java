package app.lightmove.api.core.security.model;

import java.time.Duration;

/** A first provider sign-in waiting on the terms tick: the plaintext rides the cookie only, never a body or a URL. */
public record PendingRegistrationIssued(String registrationToken, Duration expiresIn) {

    @Override
    public String toString() {
        return "PendingRegistrationIssued[expiresIn=%s]".formatted(expiresIn);
    }
}
