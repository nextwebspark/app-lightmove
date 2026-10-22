package app.lightmove.api.notetaker.service;

/** A Google access token for the API's own service account; a seam so a test can stand in for Google. */
@FunctionalInterface
public interface GoogleAccessTokens {

    String currentToken();
}
