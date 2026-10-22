package app.lightmove.api.notetaker.service;

import java.util.Optional;

/** Who a bot is, read off the Google ID token its Cloud Run job's service account minted (#956). */
public interface BotIdentityVerifier {

    /** The verified service account's email; empty for anything not a live Google ID token minted for us. */
    Optional<String> serviceAccountOf(String idToken);
}
