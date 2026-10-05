package app.lightmove.api.core.security.dto;

import java.util.UUID;

/** A workspace the consent screen offers; {@code eligible} is false where the caller is a client representative only. */
public record OAuthConsentWorkspace(UUID id, String name, boolean eligible) {}
