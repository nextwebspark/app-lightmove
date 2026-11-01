package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.AppKeysVerdict;
import app.lightmove.api.outreach.model.ProviderCredentials;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks a provider whether it accepts a workspace's own app keys. Each ask is a call to its token endpoint, so a
 * workspace has a budget of them — per instance, as every {@link RateLimiter} budget is — spent before the call,
 * whatever it answers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OwnAppKeyCheck {

    static final int CHECKS_PER_MINUTE = 10;

    private final ProviderTokenClient tokenClient;
    private final ProviderAppSetup setup;
    private final RateLimiter rateLimiter;

    /** @throws ApiException {@code RATE_LIMITED}, or {@code INTEGRATION_PROVIDER_UNREACHABLE} when it gave no answer */
    public AppKeysVerdict ask(UUID workspaceId, ProviderCredentials app) {
        if (!rateLimiter.tryAcquire("integration-key-check:workspace:%s".formatted(workspaceId), CHECKS_PER_MINUTE,
                Duration.ofMinutes(1))) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
        try {
            return tokenClient.checkKeys(app, setup.redirectUri(app.provider()));
        } catch (VendorException unanswered) {
            log.warn("{} gave no answer about workspace {}'s keys ({})", app.provider(), workspaceId,
                    unanswered.getKind());
            throw ApiException.of(ErrorCode.INTEGRATION_PROVIDER_UNREACHABLE);
        }
    }
}
