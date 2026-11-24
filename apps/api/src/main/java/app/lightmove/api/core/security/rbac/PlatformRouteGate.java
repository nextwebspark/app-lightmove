package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.security.model.AuthPrincipal;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Admits only a holder of some platform action to {@code /api/v1/platform/**}. Asked in the filter chain, before a
 * handler is looked up or a body read: a wrong method or a bad body would otherwise tell an outsider the route exists.
 */
@Component
@RequiredArgsConstructor
public class PlatformRouteGate implements AuthorizationManager<RequestAuthorizationContext> {

    private final PlatformAccess access;

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Authentication caller = authentication.get();
        return new AuthorizationDecision(caller != null && caller.getPrincipal() instanceof AuthPrincipal principal
                && access.holdsAnyAction(principal.userId()));
    }
}
