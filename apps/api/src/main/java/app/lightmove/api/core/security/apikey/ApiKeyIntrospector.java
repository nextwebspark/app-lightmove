package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.service.ClientIpResolver;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Turns a public API bearer key into its principal, re-reading everything on every request. A personal
 * key lasts only while its owner is an active member holding {@code API_KEY_MANAGE}, so a removed or
 * demoted owner's key stops at once. Every refusal is the same exception, so a caller learns nothing
 * about why.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyIntrospector implements OpaqueTokenIntrospector {

    private static final Duration USE_STAMP_INTERVAL = Duration.ofMinutes(1);

    private final ApiKeyRepository keys;
    private final WorkspaceAccess access;
    private final ClientIpResolver clientIps;
    private final Clock clock;

    @Override
    @Transactional
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        Instant now = clock.instant();
        ApiKey key = Optional.of(token)
                .filter(ApiKeySecrets::isWellFormed)
                .flatMap(secret -> keys.findByTokenHash(Tokens.hash(secret)))
                .filter(found -> found.statusAt(now) == ApiKeyStatus.ACTIVE)
                .filter(this::ownerStillEntitled)
                .orElseThrow(() -> new BadOpaqueTokenException("Invalid API key"));
        keys.stampUse(key.getId(), now, currentIp(), now.minus(USE_STAMP_INTERVAL));
        return ApiKeyPrincipal.of(key);
    }

    private boolean ownerStillEntitled(ApiKey key) {
        return key.getKind() == ApiKeyKind.SERVICE
                || access.holdsAction(key.getOwnerUserId(), key.getWorkspaceId(), WorkspaceAction.API_KEY_MANAGE);
    }

    private String currentIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? clientIps.resolve(attributes.getRequest())
                : null;
    }
}
