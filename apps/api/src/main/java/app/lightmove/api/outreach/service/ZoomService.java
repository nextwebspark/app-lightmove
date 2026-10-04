package app.lightmove.api.outreach.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.dto.ZoomResponse;
import app.lightmove.api.outreach.model.MailboxAuthorization;
import app.lightmove.api.outreach.model.MailboxConnectStart;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.model.ZoomMeeting;
import app.lightmove.api.outreach.repository.MailboxAuthorizationRepository;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consultant's own Zoom account: connected through the workspace's Zoom app, used to make the meeting a booked call
 * carries, and let go. Separate from the mailbox, which it never changes. Zoom is called outside any transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomService {

    /** The provider name an in-flight connection carries in {@code app_lm_mailbox_authorization}. */
    public static final String PROVIDER = "zoom";

    public static final String CALLBACK_PATH = ProviderAppSetup.ZOOM_CALLBACK_PATH;

    private static final String TARGET = "zoom_connection";

    private final ZoomApi zoom;
    private final ZoomTokens tokens;
    private final ZoomConnectionRepository connections;
    private final MailboxAuthorizationRepository authorizations;
    private final ProviderCredentialsResolver credentials;
    private final ProviderTokenClient tokenClient;
    private final SecretCipher cipher;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ZoomResponse view(UUID userId, UUID workspaceId) {
        Optional<ZoomConnection> connection = connections.findByWorkspaceIdAndUserId(workspaceId, userId);
        return new ZoomResponse(isOfferedTo(workspaceId),
                connection.map(ZoomConnection::getStatus).orElse(null),
                connection.map(ZoomConnection::getConnectedAt).orElse(null));
    }

    /** Book a call offers Zoom only to a consultant whose account can make a meeting now. */
    @Transactional(readOnly = true)
    public boolean isUsableBy(UUID userId, UUID workspaceId) {
        return isOfferedTo(workspaceId) && connections.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(ZoomConnection::canUse)
                .orElse(false);
    }

    @Transactional
    public MailboxConnectStart begin(UUID userId, UUID workspaceId) {
        ProviderCredentials app = requireApp(workspaceId);
        // Refused before consent: a refresh token we could not seal would be live at Zoom and discarded.
        if (!cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        authorizations.forgetZoomStartedBy(userId);
        String state = Tokens.generate();
        authorizations.save(MailboxAuthorization.started(Tokens.hash(state), workspaceId, userId, PROVIDER,
                clock.instant().plus(properties.outreach().connectWindow())));
        return new MailboxConnectStart(zoom.authorizationUri(app, state, callbackUri()), state);
    }

    /**
     * Redeems Zoom's answer. As with a mailbox, the stored state says whose account this is, and it counts only from
     * the browser that started it, which holds the same value in a cookie.
     */
    public void complete(String state, String browserState, String code, HttpServletRequest request) {
        if (state == null || code == null || !sameValue(state, browserState)) {
            throw ApiException.of(ErrorCode.MAILBOX_CONNECT_EXPIRED);
        }
        MailboxAuthorization started = authorizations.findByStateHash(Tokens.hash(state))
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_CONNECT_EXPIRED));
        // Redeemed before anything else, so a state that is refused below can never be tried again.
        if (authorizations.redeem(started.getId()) == 0 || started.hasExpired(clock.instant())
                || !PROVIDER.equals(started.getProvider())) {
            throw ApiException.of(ErrorCode.MAILBOX_CONNECT_EXPIRED);
        }
        UUID workspaceId = started.getWorkspaceId();
        UUID userId = started.getUserId();
        ProviderCredentials app = requireApp(workspaceId);

        ProviderTokenGrant granted;
        String zoomUserId;
        try {
            granted = tokenClient.redeemCode(app, code, callbackUri(), List.of());
            if (granted.refreshToken() == null) {
                throw ApiException.of(ErrorCode.ZOOM_CONNECT_FAILED);
            }
            zoomUserId = zoom.userIdOf(granted.accessToken());
        } catch (ProviderGrantRefused | ProviderAppUnavailable | VendorException failed) {
            log.warn("Zoom connection for user {} failed: {}", userId, failed.getMessage());
            throw ApiException.of(ErrorCode.ZOOM_CONNECT_FAILED);
        }
        if (!cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        String sealed = cipher.encrypt(granted.refreshToken(), ZoomConnection.refreshTokenContext(workspaceId, userId));
        String replaced = transactions.execute(status -> store(workspaceId, userId, zoomUserId, sealed));
        audit.event(WorkspaceEventType.ZOOM_CONNECTED)
                .actor(userId)
                .workspace(workspaceId)
                .target(TARGET, userId)
                .from(request)
                .record();
        if (replaced != null) {
            revokeQuietly(workspaceId, replaced);
        }
    }

    public void disconnect(UUID userId, UUID workspaceId, HttpServletRequest request) {
        String released = transactions.execute(status -> {
            ZoomConnection connection = connections.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.ZOOM_NOT_CONNECTED));
            tokens.forget(connection.getId());
            String refreshToken = readableRefreshToken(connection);
            connections.delete(connection);
            return refreshToken;
        });
        audit.event(WorkspaceEventType.ZOOM_DISCONNECTED)
                .actor(userId)
                .workspace(workspaceId)
                .target(TARGET, userId)
                .from(request)
                .record();
        if (released != null) {
            revokeQuietly(workspaceId, released);
        }
    }

    /** Never retried: a meeting made twice is two links, and the second invite would carry the wrong one. */
    public ZoomMeeting createMeeting(UUID userId, UUID workspaceId, String topic, Instant startsAt, Instant endsAt) {
        ZoomConnection connection = requireUsable(userId, workspaceId);
        int minutes = (int) Duration.between(startsAt, endsAt).toMinutes();
        return zoom.createMeeting(tokens.accessToken(connection), topic, startsAt, minutes);
    }

    /** The call it was made for never reached the calendar: the meeting goes, so no orphan link is left at Zoom. */
    public void deleteQuietly(UUID userId, UUID workspaceId, ZoomMeeting meeting) {
        try {
            ZoomConnection connection = requireUsable(userId, workspaceId);
            zoom.deleteMeeting(tokens.accessToken(connection), meeting.id());
        } catch (RuntimeException failed) {
            log.warn("Could not delete Zoom meeting {} after its call failed; it is left at Zoom", meeting.id(),
                    failed);
        }
    }

    /** The SPA's one connect-popup landing, which reports the outcome to whichever screen opened it. */
    public URI landingUri() {
        return URI.create(properties.web().baseUrl() + "/outreach/mailbox/callback");
    }

    /**
     * A reconnect to another Zoom account lets the old account's token go; the same account reconnecting keeps
     * nothing to revoke, since Zoom's revoke would also withdraw the token just issued.
     *
     * @return the replaced account's refresh token, to revoke once this commits; null otherwise
     */
    private String store(UUID workspaceId, UUID userId, String zoomUserId, String sealed) {
        Instant now = clock.instant();
        return connections.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(existing -> {
                    tokens.forget(existing.getId());
                    String previous = zoomUserId.equals(existing.getZoomUserId()) ? null
                            : readableRefreshToken(existing);
                    existing.reconnect(zoomUserId, sealed, now);
                    return previous;
                })
                .orElseGet(() -> {
                    connections.save(ZoomConnection.connected(workspaceId, userId, zoomUserId, sealed, now));
                    return null;
                });
    }

    private ZoomConnection requireUsable(UUID userId, UUID workspaceId) {
        ZoomConnection connection = transactions.execute(status ->
                connections.findByWorkspaceIdAndUserId(workspaceId, userId).orElse(null));
        if (connection == null) {
            throw ApiException.of(ErrorCode.ZOOM_NOT_CONNECTED);
        }
        if (!connection.canUse()) {
            throw ApiException.of(ErrorCode.ZOOM_RECONNECT_NEEDED);
        }
        return connection;
    }

    private String readableRefreshToken(ZoomConnection connection) {
        try {
            return cipher.decrypt(connection.getRefreshTokenEncrypted(), connection.refreshTokenContext());
        } catch (RuntimeException unreadable) {
            log.warn("Could not read the refresh token of Zoom connection {} to revoke it", connection.getId());
            return null;
        }
    }

    /** Our row is already past it; a token Zoom keeps lapses unused, and the consultant can remove the app there. */
    private void revokeQuietly(UUID workspaceId, String refreshToken) {
        try {
            credentials.resolve(workspaceId, IntegrationProvider.ZOOM)
                    .ifPresent(app -> zoom.revoke(app, refreshToken));
        } catch (RuntimeException failed) {
            log.warn("Could not revoke a released Zoom token", failed);
        }
    }

    private boolean isOfferedTo(UUID workspaceId) {
        return credentials.resolve(workspaceId, IntegrationProvider.ZOOM).isPresent();
    }

    private ProviderCredentials requireApp(UUID workspaceId) {
        return credentials.resolve(workspaceId, IntegrationProvider.ZOOM)
                .orElseThrow(() -> ApiException.of(ErrorCode.ZOOM_UNAVAILABLE));
    }

    private URI callbackUri() {
        return URI.create(properties.web().baseUrl() + CALLBACK_PATH);
    }

    private static boolean sameValue(String expected, String presented) {
        return presented != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
