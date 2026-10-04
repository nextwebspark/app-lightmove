package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The verification token in a JSON body, never the query string. The route mints a session and is
 * CSRF-exempt, so a {@code @RequestParam} handler would let a cross-site form POST plant its refresh
 * cookie; JSON forces the preflight, and keeps the credential out of logs and {@code Referer}.
 */
public record VerifyEmailRequest(@NotBlank String token) {}
