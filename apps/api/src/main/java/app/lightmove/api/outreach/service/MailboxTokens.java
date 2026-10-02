package app.lightmove.api.outreach.service;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.RefreshedAccessToken;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Access tokens for our own gateway's mailboxes, from each connection's stored refresh token and the OAuth app its
 * workspace connects through. An access token is held in memory only, until shortly before it expires; the
 * refresh token is decrypted for the one call that spends it. Neither is ever logged.
 *
 * <p>A refresh the provider refuses is what Nylas's {@code grant.expired} was: the mailbox is marked for
 * reconnecting, its Recall calendar released, and the caller gets the {@code CREDENTIALS} failure every sender
 * already reads as "reconnect needed".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailboxTokens {

    /** Spent this long before the provider's expiry, so a token never lapses mid-request. */
    static final Duration EXPIRY_MARGIN = Duration.ofMinutes(2);

    static final String VENDOR = "mailbox-token";

    private final MailboxConnectionRepository connections;
    private final ProviderCredentialsResolver credentials;
    private final SecretCipher cipher;
    private final ProviderTokenClient tokenClient;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    private final Map<String, HeldAccessToken> held = new ConcurrentHashMap<>();

    public String accessToken(String grantId) {
        Instant now = clock.instant();
        HeldAccessToken cached = held.get(grantId);
        if (cached != null && cached.isUsableAt(now)) {
            return cached.accessToken();
        }
        MailboxConnection connection = transactions.execute(status -> connections.findByGrantId(grantId).stream()
                .filter(MailboxConnection::isDirect)
                .findFirst()
                .orElse(null));
        if (connection == null || !connection.canSend()) {
            throw withdrawn("no usable connection");
        }
        ProviderCredentials app = credentials.resolve(connection.getWorkspaceId(), connection.integrationProvider())
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE));
        String refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted(), connection.refreshTokenContext());

        RefreshedAccessToken refreshed;
        try {
            refreshed = tokenClient.refresh(app, refreshToken);
        } catch (RefreshTokenRefused refused) {
            held.remove(grantId);
            withdrawAccess(connection.getId(), grantId);
            log.info("The provider refused the refresh token of mailbox {} ({}); it needs reconnecting",
                    connection.getId(), refused.getMessage());
            throw withdrawn("refresh token refused");
        }
        if (refreshed.rotatedRefreshToken() != null && !refreshed.rotatedRefreshToken().equals(refreshToken)) {
            keepRotated(connection.getId(), grantId, refreshed.rotatedRefreshToken());
        }
        held.put(grantId, new HeldAccessToken(refreshed.accessToken(), now.plus(refreshed.expiresIn())));
        return refreshed.accessToken();
    }

    /** A disconnected or replaced grant's token must not outlive it in memory. */
    public void forget(String grantId) {
        held.remove(grantId);
    }

    private void keepRotated(UUID connectionId, String grantId, String rotated) {
        transactions.executeWithoutResult(status -> connections.findById(connectionId)
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .ifPresent(fresh -> fresh.rotateRefreshToken(cipher.encrypt(rotated, fresh.refreshTokenContext()))));
    }

    private void withdrawAccess(UUID connectionId, String grantId) {
        transactions.executeWithoutResult(status -> connections.findById(connectionId)
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .ifPresent(fresh -> {
                    fresh.markAccessWithdrawn();
                    String released = fresh.releaseRecallCalendar();
                    if (released != null) {
                        events.publishEvent(new RecallCalendarReleased(released));
                    }
                }));
    }

    private static VendorException withdrawn(String why) {
        return new VendorException(VendorCall.of(VENDOR, why), VendorFailureKind.CREDENTIALS, null);
    }

    private record HeldAccessToken(String accessToken, Instant expiresAt) {

        HeldAccessToken {
            Objects.requireNonNull(accessToken, "accessToken");
        }

        boolean isUsableAt(Instant now) {
            return now.isBefore(expiresAt.minus(EXPIRY_MARGIN));
        }

        @Override
        public String toString() {
            return "HeldAccessToken[expiresAt=" + expiresAt + "]";
        }
    }
}
