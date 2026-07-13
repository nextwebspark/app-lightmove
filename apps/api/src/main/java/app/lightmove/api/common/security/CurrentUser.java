package app.lightmove.api.common.security;

import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated {@link AuthPrincipal} out of the security context.
 *
 * <p>The one supported way to learn who is calling. A controller must never take a user id or a
 * workspace id as a request parameter and trust it — that is precisely the bug that lets one firm read
 * another's candidate pipeline. Identity comes from the signed token, and only from there.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** For endpoints that are already behind authentication — if this throws, the filter chain is wrong. */
    public static AuthPrincipal require() {
        return find().orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
