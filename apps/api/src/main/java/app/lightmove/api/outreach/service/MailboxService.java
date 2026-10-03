package app.lightmove.api.outreach.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.outreach.dto.ConnectedMailboxResponse;
import app.lightmove.api.outreach.dto.MailboxResponse;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxAuthorization;
import app.lightmove.api.outreach.model.MailboxConnected;
import app.lightmove.api.outreach.model.MailboxConnectStart;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.repository.MailboxAuthorizationRepository;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consultant's own mailbox: connecting it through the mail service's hosted sign-in, sending a test
 * from it, and letting it go. The mail service is called outside any transaction, between the writes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailboxService {

    public static final String CALLBACK_PATH = "/api/v1/outreach/mailbox/callback";

    private static final String TARGET = "mailbox_connection";

    private static final String TEST_SUBJECT = "Your mailbox is connected to Uncava";
    private static final String TEST_BODY = "<p>This is a test from Uncava.</p>"
            + "<p>Outreach you start in Uncava will go from this mailbox, and replies will land here, in your inbox.</p>";

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository connections;
    private final MailboxAuthorizationRepository authorizations;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final BookingPages bookingPages;
    private final MailboxTokens tokens;
    private final SecretCipher cipher;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MailboxResponse view(UUID userId, UUID workspaceId) {
        ConnectedMailboxResponse connection = connections.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(mailbox -> ConnectedMailboxResponse.of(mailbox, bookingPages.linkOf(mailbox)))
                .orElse(null);
        return new MailboxResponse(gateway.isOffered(), gateway.providers(), connection,
                bookingPages.isOffered());
    }

    /** Any attempt the caller left unfinished is dropped, so only the newest consent screen can connect. */
    @Transactional
    public MailboxConnectStart begin(UUID userId, UUID workspaceId, String loginHint, String provider) {
        requireOffered();
        if (!gateway.providers().contains(provider)) {
            throw ApiException.of(ErrorCode.MAILBOX_PROVIDER_UNSUPPORTED);
        }
        // Refused before consent: a refresh token we could not seal would be live at the provider and discarded.
        if (gateway.holdsRefreshTokens(provider) && !cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        authorizations.forgetStartedBy(userId);
        String state = Tokens.generate();
        authorizations.save(MailboxAuthorization.started(Tokens.hash(state), workspaceId, userId, provider,
                clock.instant().plus(settings().connectWindow())));
        URI consent = gateway.authorizationUri(workspaceId, provider, loginHint, state, callbackUri());
        return new MailboxConnectStart(consent, state);
    }

    /**
     * Redeems the consent screen's answer. The callback carries no bearer token, so the stored state is
     * what says whose mailbox this is — and it counts only from the browser that started it, which holds
     * the same value in a cookie: a consent link handed to someone else would otherwise connect their
     * mailbox to the account that minted it.
     */
    public void complete(String state, String browserState, String code, HttpServletRequest request) {
        if (state == null || code == null || !sameValue(state, browserState)) {
            throw ApiException.of(ErrorCode.MAILBOX_CONNECT_EXPIRED);
        }
        MailboxAuthorization started = authorizations.findByStateHash(Tokens.hash(state))
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_CONNECT_EXPIRED));
        // Redeemed before anything else, so a state that is refused below can never be tried again.
        if (authorizations.redeem(started.getId()) == 0 || started.hasExpired(clock.instant())) {
            throw ApiException.of(ErrorCode.MAILBOX_CONNECT_EXPIRED);
        }

        GrantedMailbox granted;
        try {
            granted = gateway.redeem(started.getWorkspaceId(), started.getProvider(), code, callbackUri());
        } catch (VendorException failed) {
            log.warn("Mailbox connection for user {} failed at the mail service: {}", started.getUserId(),
                    failed.getKind());
            throw ApiException.of(ErrorCode.MAILBOX_CONNECT_FAILED);
        }

        String replacedGrant = transactions.execute(status -> store(started, granted));
        audit.event(WorkspaceEventType.MAILBOX_CONNECTED)
                .actor(started.getUserId())
                .workspace(started.getWorkspaceId())
                .target(TARGET, started.getUserId())
                .from(request)
                .detail("provider", granted.provider())
                .record();
        if (replacedGrant != null && !replacedGrant.equals(granted.grantId())) {
            tokens.forget(replacedGrant);
            revokeQuietly(replacedGrant);
        }
    }

    public void disconnect(UUID userId, UUID workspaceId, HttpServletRequest request) {
        String grantId = transactions.execute(status -> {
            MailboxConnection connection = requireConnection(userId, workspaceId);
            String recallCalendar = connection.releaseRecallCalendar();
            if (recallCalendar != null) {
                events.publishEvent(new RecallCalendarReleased(recallCalendar));
            }
            connections.delete(connection);
            return connection.getGrantId();
        });
        tokens.forget(grantId);
        audit.event(WorkspaceEventType.MAILBOX_DISCONNECTED)
                .actor(userId)
                .workspace(workspaceId)
                .target(TARGET, userId)
                .from(request)
                .record();
        revokeQuietly(grantId);
    }

    /** Sends one email from the mailbox to itself, which is the only proof that it can send at all. */
    public void sendTest(UUID userId, UUID workspaceId) {
        requireOffered();
        MailboxConnection connection = connections.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_CONNECTED));
        if (!connection.canSend()) {
            throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
        }
        try {
            gateway.send(connection.getGrantId(), new OutgoingEmail(connection.getAddress(), TEST_SUBJECT, TEST_BODY));
        } catch (VendorException failed) {
            if (isAccessWithdrawn(failed)) {
                transactions.executeWithoutResult(status -> requireConnection(userId, workspaceId).markAccessWithdrawn());
                throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
            }
            log.warn("Test send for user {} failed at the mail service: {}", userId, failed.getKind());
            throw ApiException.of(ErrorCode.MAILBOX_SEND_FAILED);
        }
    }

    /** The browser lands here after the callback, in the connect popup or as a full page. */
    public URI landingUri() {
        return URI.create(properties.web().baseUrl() + "/outreach/mailbox/callback");
    }

    private String store(MailboxAuthorization started, GrantedMailbox granted) {
        Instant now = clock.instant();
        String refreshTokenEncrypted = encryptedRefreshToken(started, granted);
        return connections.findByWorkspaceIdAndUserId(started.getWorkspaceId(), started.getUserId())
                .map(existing -> {
                    String previous = existing.getGrantId();
                    String movedFrom = existing.releaseRecallCalendarUnlessFor(granted);
                    if (movedFrom != null) {
                        events.publishEvent(new RecallCalendarReleased(movedFrom));
                    }
                    existing.reconnect(granted, refreshTokenEncrypted, now);
                    events.publishEvent(new MailboxConnected(existing.getId()));
                    return previous;
                })
                .orElseGet(() -> {
                    MailboxConnection connected = connections.save(MailboxConnection.connected(
                            started.getWorkspaceId(), started.getUserId(), granted, refreshTokenEncrypted,
                            settings().dailyCap(), now));
                    events.publishEvent(new MailboxConnected(connected.getId()));
                    return null;
                });
    }

    /** Our own gateway's refresh token, sealed before it reaches the row; Nylas's grants carry none. */
    private String encryptedRefreshToken(MailboxAuthorization started, GrantedMailbox granted) {
        if (granted.refreshToken() == null) {
            return null;
        }
        if (!cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        return cipher.encrypt(granted.refreshToken(),
                MailboxConnection.refreshTokenContext(started.getWorkspaceId(), started.getUserId()));
    }

    private MailboxConnection requireConnection(UUID userId, UUID workspaceId) {
        return connections.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_CONNECTED));
    }

    /** Our row is already gone; a grant the mail service keeps is its housekeeping, not the consultant's problem. */
    private void revokeQuietly(String grantId) {
        try {
            gateway.revoke(grantId);
        } catch (RuntimeException failed) {
            log.warn("Could not revoke a released mailbox grant at the mail service", failed);
        }
    }

    /** A revoked grant answers as the grant being unknown or unauthorised; either way only reconnecting helps. */
    static boolean isAccessWithdrawn(VendorException failed) {
        return failed.getKind() == VendorFailureKind.CREDENTIALS || failed.getKind() == VendorFailureKind.NOT_FOUND;
    }

    private void requireOffered() {
        if (!gateway.isOffered()) {
            throw ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
        }
    }

    private URI callbackUri() {
        return URI.create(properties.web().baseUrl() + CALLBACK_PATH);
    }

    private OutreachSettings settings() {
        return properties.outreach();
    }

    private static boolean sameValue(String expected, String presented) {
        return presented != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
