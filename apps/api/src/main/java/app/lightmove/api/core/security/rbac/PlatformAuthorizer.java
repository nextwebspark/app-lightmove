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

    public boolean can(AuthPrincipal principal, String action) {
        access.requireAction(principal.userId(), PlatformAction.valueOf(action));
        return true;
    }
}
