package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The platform-tier guard bean behind {@code @PreAuthorize("@platformAuthorizer.can(principal, 'X')")},
 * on {@link WorkspaceAuthorizer}'s contract: returns true, denies by throwing. It asks nothing of the
 * caller's workspace and grants nothing inside one.
 */
@Component("platformAuthorizer")
@RequiredArgsConstructor
public class PlatformAuthorizer {

    private final PlatformAccess access;
    private final PlatformTwoFactor twoFactor;

    /**
     * The action first, then the factor: a caller without the action is answered 404 before anything says the
     * route asks for two-factor, so the platform surface is still not advertised.
     */
    public boolean can(AuthPrincipal principal, String action) {
        access.requireAction(principal.userId(), PlatformAction.valueOf(action));
        twoFactor.requireEnrolled(principal.userId());
        return true;
    }

    /** A high-impact action: the action, the factor, and a code entered within the step-up window. */
    public boolean canWithRecentTwoFactor(AuthPrincipal principal, String action) {
        can(principal, action);
        twoFactor.requireRecentCode(principal.userId());
        return true;
    }

    /** Anyone holding a platform action at all: the step-up itself, which belongs to no single action. */
    public boolean holdsAnyAction(AuthPrincipal principal) {
        access.requireAnyAction(principal.userId());
        twoFactor.requireEnrolled(principal.userId());
        return true;
    }
}
