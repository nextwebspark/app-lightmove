package app.lightmove.api.outreach.service;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.RefreshedAccessToken;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Access tokens for our own gateway's mailboxes, from each connection's stored refresh token and the OAuth app its
 * workspace connects through. An access token is held in memory only, until shortly before it expires, and one
 * refresh runs per grant at a time — Microsoft rotates the refresh token, so two racing would each keep a different
 * one. Neither token is ever logged.
 *
 * <p>A refresh the provider refuses is what Nylas's {@code grant.expired} was: the mailbox is marked for
 * reconnecting, its Recall calendar released, and the caller gets the {@code CREDENTIALS} failure every sender
 * already reads as "reconnect needed". A refused <em>app</em> is {@link ProviderAppUnavailable}, never that.
 */
@Service
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
    private final Cache<String, HeldAccessToken> held;

    public MailboxTokens(MailboxConnectionRepository connections, ProviderCredentialsResolver credentials,
                         SecretCipher cipher, ProviderTokenClient tokenClient, TransactionTemplate transactions,
                         ApplicationEventPublisher events, Clock clock) {
        this.connections = connections;
        this.credentials = credentials;
        this.cipher = cipher;
        this.tokenClient = tokenClient;
        this.transactions = transactions;
        this.events = events;
        this.held = Caffeine.newBuilder()
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .executor(Runnable::run)
                .expireAfter(Expiry.creating((String grantId, HeldAccessToken token) -> token.usableFor()))
                .build();
    }

    /** A cached token is handed out only while its mailbox is still active: access withdrawn ends it at once. */
    public String accessToken(String grantId) {
        HeldAccessToken cached = held.getIfPresent(grantId);
        if (cached != null) {
            if (connections.existsByGrantIdAndStatus(grantId, MailboxStatus.ACTIVE)) {
                return cached.accessToken();
            }
            held.invalidate(grantId);
            throw withdrawn("mailbox no longer active");
        }
        return held.get(grantId, this::refresh).accessToken();
    }

    /** A disconnected or replaced grant's token must not outlive it in memory. */
    public void forget(String grantId) {
        held.invalidate(grantId);
    }

    private HeldAccessToken refresh(String grantId) {
        MailboxConnection connection = transactions.execute(status -> connections.findByGrantId(grantId).stream()
                .filter(MailboxConnection::isDirect)
                .findFirst()
                .orElse(null));
        if (connection == null || !connection.canSend()) {
            throw withdrawn("no usable connection");
        }
        ProviderCredentials app = credentials.resolve(connection.getWorkspaceId(), connection.integrationProvider())
                .orElseThrow(() -> new ProviderAppUnavailable("no app resolves"));
        String refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted(), connection.refreshTokenContext());

        RefreshedAccessToken refreshed;
        try {
            refreshed = tokenClient.refresh(app, refreshToken);
        } catch (RefreshTokenRefused refused) {
            withdrawAccess(connection.getId(), grantId);
            log.info("The provider refused the refresh token of mailbox {} ({}); it needs reconnecting",
                    connection.getId(), refused.getMessage());
            throw withdrawn("refresh token refused");
        } catch (ProviderAppUnavailable appRefused) {
            log.warn("The {} app of workspace {} was refused ({}); its mailboxes wait for an admin to fix it",
                    app.provider(), connection.getWorkspaceId(), appRefused.getMessage());
            throw appRefused;
        }
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().equals(refreshToken)) {
            keepRotated(connection.getId(), grantId, refreshed.refreshToken());
        }
        return new HeldAccessToken(refreshed.accessToken(), refreshed.expiresIn().minus(EXPIRY_MARGIN));
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

    private record HeldAccessToken(String accessToken, Duration usableFor) {

        HeldAccessToken {
            usableFor = usableFor.isNegative() ? Duration.ZERO : usableFor;
        }

        @Override
        public String toString() {
            return "HeldAccessToken[usableFor=" + usableFor + "]";
        }
    }
}
