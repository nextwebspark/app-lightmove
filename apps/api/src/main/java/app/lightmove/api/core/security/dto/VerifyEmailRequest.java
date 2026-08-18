package app.lightmove.api.core.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The emailed verification token, in the body rather than the query string.
 *
 * <p>A body is what protects this route. Redeeming now mints a session and sets the refresh cookie, and
 * the route is CSRF-exempt because it must work on a first visit with no token to echo. A handler
 * taking only a {@code @RequestParam} is reachable by a cross-site HTML form POST — a CORS-simple
 * request, no preflight — which would let any site plant its own refresh cookie in a visitor's browser
 * and have the SPA adopt it on next boot. Requiring {@code application/json} forces the preflight that
 * already protects {@code /auth/login} and {@code /auth/password/reset}, both of which mint sessions
 * from the same exemption list.
 *
 * <p>It also keeps a live credential out of the URL, and so out of access logs and {@code Referer}.
 */
public record VerifyEmailRequest(@NotBlank String token) {}
