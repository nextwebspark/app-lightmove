package app.lightmove.api.core.security.oauth;

import java.time.Duration;

/** A metadata document's body as its host served it, and how long that host said it may be kept. */
public record FetchedClientMetadata(String body, Duration lifetime) {}
