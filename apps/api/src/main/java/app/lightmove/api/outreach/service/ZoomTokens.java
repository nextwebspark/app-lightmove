package app.lightmove.api.outreach.service;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Access tokens for consultants' Zoom accounts, from each connection's stored refresh token and the workspace's Zoom
 * app. Held in memory only, until shortly before they expire, one refresh per connection at a time: Zoom rotates the
 * refresh token on every use, so two racing would each keep a different one and the loser's would be dead.
 *
 * <p>A refresh Zoom refuses marks the connection withdrawn and answers {@code ZOOM_RECONNECT_NEEDED}; a refused
 * app answers {@code ZOOM_UNAVAILABLE} and leaves the connection as it is, since an admin fixing the app fixes it.
 */
@Service
@Slf4j
public class ZoomTokens {

    private final ZoomConnectionRepository connections;
    private final ProviderCredentialsResolver credentials;
    private final SecretCipher cipher;
    private final ProviderTokenClient tokenClient;
    private final TransactionTemplate transactions;
    private final Cache<UUID, HeldZoomToken> held;

    public ZoomTokens(ZoomConnectionRepository connections, ProviderCredentialsResolver credentials,
                      SecretCipher cipher, ProviderTokenClient tokenClient, TransactionTemplate transactions,
                      Clock clock) {
        this.connections = connections;
        this.credentials = credentials;
        this.cipher = cipher;
        this.tokenClient = tokenClient;
        this.transactions = transactions;
        this.held = Caffeine.newBuilder()
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .executor(Runnable::run)
                .expireAfter(Expiry.creating((UUID connectionId, HeldZoomToken token) -> token.usableFor()))
                .build();
    }

    public String accessToken(ZoomConnection connection) {
        if (!connection.canUse()) {
            held.invalidate(connection.getId());
            throw ApiException.of(ErrorCode.ZOOM_RECONNECT_NEEDED);
        }
        return held.get(connection.getId(), this::refresh).accessToken();
    }

    public void forget(UUID connectionId) {
        held.invalidate(connectionId);
    }

    private HeldZoomToken refresh(UUID connectionId) {
        ZoomConnection connection = transactions.execute(status -> connections.findById(connectionId).orElse(null));
        if (connection == null || !connection.canUse()) {
            throw ApiException.of(ErrorCode.ZOOM_RECONNECT_NEEDED);
        }
        ProviderCredentials app = credentials.resolve(connection.getWorkspaceId(), IntegrationProvider.ZOOM)
                .orElseThrow(() -> ApiException.of(ErrorCode.ZOOM_UNAVAILABLE));
        String refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted(), connection.refreshTokenContext());
        ProviderTokenGrant refreshed;
        try {
            refreshed = tokenClient.refresh(app, refreshToken);
        } catch (ProviderGrantRefused refused) {
            transactions.executeWithoutResult(status -> connections.findById(connectionId)
                    .ifPresent(ZoomConnection::markAccessWithdrawn));
            log.info("Zoom refused the refresh token of connection {} ({}); it needs reconnecting", connectionId,
                    refused.getMessage());
            throw ApiException.of(ErrorCode.ZOOM_RECONNECT_NEEDED);
        } catch (ProviderAppUnavailable appRefused) {
            log.warn("The Zoom app of workspace {} was refused ({})", connection.getWorkspaceId(),
                    appRefused.getMessage());
            throw ApiException.of(ErrorCode.ZOOM_UNAVAILABLE);
        }
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().equals(refreshToken)) {
            transactions.executeWithoutResult(status -> connections.findById(connectionId)
                    .ifPresent(fresh -> fresh.rotateRefreshToken(
                            cipher.encrypt(refreshed.refreshToken(), fresh.refreshTokenContext()))));
        }
        return new HeldZoomToken(refreshed.accessToken(), refreshed.expiresIn().minus(MailboxTokens.EXPIRY_MARGIN));
    }

    private record HeldZoomToken(String accessToken, Duration usableFor) {

        HeldZoomToken {
            usableFor = usableFor.isNegative() ? Duration.ZERO : usableFor;
        }

        @Override
        public String toString() {
            return "HeldZoomToken[usableFor=" + usableFor + "]";
        }
    }
}
