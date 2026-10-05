package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.service.ClientIpResolver;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Turns a public API key into its principal, re-reading the key and, for a personal key, its owner's
 * {@code API_KEY_MANAGE} on every request, so a removed or demoted owner's key stops at once. Every
 * refusal is the same exception; only a spent budget answers differently.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyIntrospector implements OpaqueTokenIntrospector {

    private static final Duration USE_STAMP_INTERVAL = Duration.ofMinutes(1);

    private final ApiKeyRepository keys;
    private final WorkspaceAccess access;
    private final PublicApiBudget budget;
    private final ClientIpResolver clientIps;
    private final Clock clock;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        String ip = currentIp();
        if (ip != null) {
            budget.spendForIp(ip);
        }
        if (!ApiKeySecrets.isWellFormed(token)) {
            throw refused();
        }
        String tokenHash = Tokens.hash(token);
        budget.spendForKey(tokenHash);

        Instant now = clock.instant();
        ApiKey key = keys.findByTokenHash(tokenHash)
                .filter(found -> found.statusAt(now) == ApiKeyStatus.ACTIVE)
                .filter(this::ownerStillEntitled)
                .orElseThrow(ApiKeyIntrospector::refused);
        ApiKeyPrincipal principal = ApiKeyPrincipal.of(key).orElseThrow(ApiKeyIntrospector::refused);

        Instant staleBefore = now.minus(USE_STAMP_INTERVAL);
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(staleBefore)) {
            keys.stampUse(key.getId(), now, ip, staleBefore);
        }
        return principal;
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

    private static BadOpaqueTokenException refused() {
        return new BadOpaqueTokenException("Invalid API key");
    }
}
