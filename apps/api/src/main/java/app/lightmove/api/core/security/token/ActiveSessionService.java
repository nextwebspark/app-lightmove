package app.lightmove.api.core.security.token;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.ActiveSessionResponse;
import app.lightmove.api.core.security.model.DeviceDescription;
import app.lightmove.api.core.security.service.DeviceDescriber;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Active sessions: what is signed in, and ending any of it.
 *
 * <p>A session is a refresh-token <b>family</b>. Rotation revokes each token as it mints its successor,
 * so a live family has exactly one live token — which makes the list a plain query and the family id a
 * name for a session that survives the rotations happening underneath it.
 *
 * <p>Every method identifies the caller's own session from the refresh cookie they presented. Without
 * it nothing here can be answered honestly: no row could be marked as theirs, and "sign out all others"
 * would sign them out of the tab they are looking at.
 */
@Service
@RequiredArgsConstructor
public class ActiveSessionService {

    private static final Comparator<ActiveSessionResponse> CURRENT_FIRST_THEN_MOST_RECENT =
            Comparator.<ActiveSessionResponse, Boolean>comparing(ActiveSessionResponse::current,
                            Comparator.reverseOrder())
                    .thenComparing(ActiveSessionResponse::lastActiveAt, Comparator.reverseOrder());

    private final RefreshTokenRepository refreshTokens;
    private final DeviceDescriber deviceDescriber;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ActiveSessionResponse> list(UUID userId, String presentedRefreshToken) {
        return liveSessions(userId, currentFamilyId(userId, presentedRefreshToken));
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId, String presentedRefreshToken, HttpServletRequest request) {
        if (currentFamilyId(userId, presentedRefreshToken).equals(sessionId)) {
            throw ApiException.of(ErrorCode.CURRENT_SESSION_NOT_REVOCABLE);
        }

        int revoked = refreshTokens.revokeFamilyForUser(
                sessionId, userId, RevokeReason.USER_REVOKED, Instant.now());
        if (revoked == 0) {
            throw ApiException.of(ErrorCode.SESSION_NOT_FOUND);
        }

        audit.event(AuthEventType.SESSION_REVOKED).actor(userId).from(request)
                .detail("sessionId", sessionId.toString()).record();
    }

    /** @return how many sessions ended, for the confirmation the SPA shows. */
    @Transactional
    public int revokeOthers(UUID userId, String presentedRefreshToken, HttpServletRequest request) {
        UUID keptFamilyId = currentFamilyId(userId, presentedRefreshToken);

        // Counted from the live list rather than from the update's row count: an expired token that was
        // never revoked still satisfies `revoked_at IS NULL`, so the update would report sessions the
        // user was never shown.
        int ended = (int) liveSessions(userId, keptFamilyId).stream()
                .filter(session -> !session.current())
                .count();

        refreshTokens.revokeAllForUserExceptFamily(
                userId, keptFamilyId, RevokeReason.USER_REVOKED, Instant.now());

        audit.event(AuthEventType.OTHER_SESSIONS_REVOKED).actor(userId).from(request)
                .detail("sessions", ended).record();

        return ended;
    }

    private List<ActiveSessionResponse> liveSessions(UUID userId, UUID currentFamilyId) {
        return refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, Instant.now()).stream()
                .map(token -> describe(token, currentFamilyId))
                .sorted(CURRENT_FIRST_THEN_MOST_RECENT)
                .toList();
    }

    private ActiveSessionResponse describe(RefreshToken token, UUID currentFamilyId) {
        DeviceDescription device = deviceDescriber.describe(token.getUserAgent());
        return new ActiveSessionResponse(
                token.getFamilyId(),
                device.label(),
                device.kind(),
                token.getIpAddress(),
                token.getCreatedAt(),
                token.getFamilyId().equals(currentFamilyId));
    }

    /**
     * The family the caller is signed in on. Rejects a cookie that is missing, dead, or somebody else's
     * — the last of which would otherwise let a caller mark another user's session as their own.
     */
    private UUID currentFamilyId(UUID userId, String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "No refresh cookie on the request");
        }

        return refreshTokens.findByTokenHash(Tokens.hash(presentedRefreshToken))
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> token.isRedeemable(Instant.now()))
                .map(RefreshToken::getFamilyId)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID,
                        "Refresh cookie does not name a live session for user " + userId));
    }
}
