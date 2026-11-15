package app.lightmove.api.core.security.dto;

/**
 * What the terms step shows before anything exists: the details the provider gave, and its registration id for the
 * button's mark. Only the browser holding the cookie can read them.
 */
public record PendingRegistrationResponse(String provider, String email, String fullName) {}
