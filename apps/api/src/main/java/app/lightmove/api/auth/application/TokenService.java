package app.lightmove.api.auth.application;

import app.lightmove.api.auth.domain.RefreshToken;
import app.lightmove.api.auth.domain.RevokeReason;
import app.lightmove.api.auth.domain.User;
import app.lightmove.api.auth.infrastructure.RefreshTokenRepository;
import app.lightmove.api.common.audit.AuditEventType;
import app.lightmove.api.common.audit.AuditService;
import app.lightmove.api.common.config.LightMoveProperties;
import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import app.lightmove.api.common.security.Tokens;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes sessions.
 *
 * <p>Everything about the shape of a LightMove session lives here: what an access token claims, how a
 * refresh token rotates, and what happens when a stolen one is replayed.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final LightMoveProperties.Auth config;

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens,
                        AuditService audit, LightMoveProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.config = properties.auth();
    }

    /** A fresh login: a new access token and a refresh token that opens a new family. */
    @Transactional
    public AuthenticatedSession issue(User user, WorkspaceMember membership, HttpServletRequest request) {
        Instant now = Instant.now();

        String plaintext = Tokens.generate();
        RefreshToken token = RefreshToken.issue(
                user.getId(),
                Tokens.hash(plaintext),
                now.plus(config.refreshTokenTtl()),
                userAgent(request),
                clientIp(request));
        refreshTokens.save(token);

        TokenPair pair = new TokenPair(mintAccessToken(user, membership, now), config.accessTokenTtl(), plaintext);
        return new AuthenticatedSession(pair, user, membership);
    }

    /**
     * Redeems a refresh token for a new pair, and burns the one presented.
     *
     * <p>This is where theft is caught. A token that has already been rotated away should never be
     * seen again — so if one is, either an attacker stole it and is redeeming it, or they stole and
     * redeemed it already and this is the real user arriving with a token that is now stale. The two
     * are indistinguishable from here, and in both cases someone unauthorised holds a live credential.
     * So the whole family dies and both parties sign in again. A lost session is an acceptable cost;
     * leaving a thief with a working token is not.
     *
     * <p><b>{@code noRollbackFor = ApiException.class} is what makes the revocation stick.</b> Spring
     * rolls back on any unchecked exception, and {@link ApiException} is one — so {@link #handleReuse}
     * would revoke the entire token family, throw REFRESH_TOKEN_REUSED, and have the revocation rolled
     * straight back out with the transaction. We would detect the theft, tell the attacker we had
     * detected it, and leave every one of their stolen tokens working. The detection would be theatre.
     *
     * @param membershipLookup resolves the user's current membership, so that a role change or a
     *                         removal takes effect at the next refresh — within {@code accessTokenTtl}
     *                         — rather than persisting until the session finally expires.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession rotate(String presentedToken, HttpServletRequest request,
                                       UserLookup userLookup, MembershipLookup membershipLookup) {
        Instant now = Instant.now();
        String presentedHash = Tokens.hash(presentedToken);

        RefreshToken existing = refreshTokens.findByTokenHash(presentedHash)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID,
                        "No refresh token matches the presented hash"));

        if (existing.isRevoked()) {
            handleReuse(existing, request, now);
        }

        if (existing.isExpired(now)) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token expired");
        }

        User user = userLookup.byId(existing.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "User no longer exists"));

        // A user suspended mid-session should not be able to refresh their way through the suspension.
        if (!user.getStatus().canAuthenticate()) {
            refreshTokens.revokeFamily(existing.getFamilyId(), RevokeReason.ADMIN_REVOKED, now);
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "User cannot authenticate: " + user.getStatus());
        }

        String plaintext = Tokens.generate();
        RefreshToken successor = RefreshToken.issueInFamily(
                user.getId(),
                Tokens.hash(plaintext),
                existing.getFamilyId(),
                now.plus(config.refreshTokenTtl()),
                userAgent(request),
                clientIp(request));
        refreshTokens.save(successor);

        existing.rotateTo(successor, now);

        audit.event(AuditEventType.TOKEN_REFRESHED).actor(user.getId()).from(request).record();

        WorkspaceMember membership = membershipLookup.forUser(user.getId()).orElse(null);
        TokenPair pair = new TokenPair(mintAccessToken(user, membership, now), config.accessTokenTtl(), plaintext);
        return new AuthenticatedSession(pair, user, membership);
    }

    /** Ends one session. Idempotent — signing out twice is not an error. */
    @Transactional
    public void revoke(String presentedToken, HttpServletRequest request) {
        refreshTokens.findByTokenHash(Tokens.hash(presentedToken)).ifPresent(token -> {
            token.revoke(RevokeReason.LOGOUT, Instant.now());
            audit.event(AuditEventType.LOGOUT).actor(token.getUserId()).from(request).record();
        });
    }

    /** Signs a user out everywhere. Used on password change: whoever knew the old password is out. */
    @Transactional
    public void revokeAllSessions(UUID userId, RevokeReason reason) {
        int revoked = refreshTokens.revokeAllForUser(userId, reason, Instant.now());
        log.debug("Revoked {} session(s) for user {} ({})", revoked, userId, reason);
    }

    private void handleReuse(RefreshToken replayed, HttpServletRequest request, Instant now) {
        int killed = refreshTokens.revokeFamily(replayed.getFamilyId(), RevokeReason.REUSE_DETECTED, now);

        // The one event in the system that should page a human: a refresh token was replayed, which
        // means one leaked.
        log.warn("Refresh token reuse detected for user {} — revoked {} token(s) in family {}",
                replayed.getUserId(), killed, replayed.getFamilyId());

        audit.event(AuditEventType.TOKEN_REUSE_DETECTED)
                .failed()
                .actor(replayed.getUserId())
                .from(request)
                .detail("familyId", replayed.getFamilyId().toString())
                .detail("tokensRevoked", killed)
                .detail("originallyRevokedAs", String.valueOf(replayed.getRevokedReason()))
                .record();

        throw ApiException.of(ErrorCode.REFRESH_TOKEN_REUSED);
    }

    /**
     * The access token. Short-lived, signed RS256, and carrying just enough to authorise a request
     * without a database round-trip.
     *
     * <p>{@code wsId} and {@code role} are the tenant claims — signed, so a caller cannot alter them.
     * Both are absent for a user who has not yet finished signup step 2: they exist but have no
     * workspace, and the filter chain lets them reach only the onboarding endpoints.
     *
     * <p>The trade-off in putting the role in the token is staleness: revoking someone's admin rights
     * does not reach an already-minted token. That window is bounded by {@code accessTokenTtl} — 15
     * minutes — because the next refresh re-reads the membership. Shrinking the TTL shrinks the window.
     */
    private String mintAccessToken(User user, WorkspaceMember membership, Instant now) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(config.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(config.accessTokenTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("emailVerified", user.isEmailVerified())
                // A unique id per token, so a specific one can be denylisted later without a scheme change.
                .id(UUID.randomUUID().toString());

        if (membership != null && membership.isActive()) {
            claims.claim("wsId", membership.getWorkspaceId().toString());
            claims.claim("role", membership.getRole().name());
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader("User-Agent");
        return value != null && value.length() > 512 ? value.substring(0, 512) : value;
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Lookups passed in rather than injected, to keep this service free of a dependency on the user
     * and workspace repositories — it deals in tokens. It also breaks what would otherwise be a
     * circular wiring between TokenService and AuthService.
     */
    @FunctionalInterface
    public interface UserLookup {
        java.util.Optional<User> byId(UUID userId);
    }

    @FunctionalInterface
    public interface MembershipLookup {
        java.util.Optional<WorkspaceMember> forUser(UUID userId);
    }

    /** Exposed for the controller, which needs it to decide how long the refresh cookie lives. */
    public java.time.Duration refreshTokenTtl() {
        return config.refreshTokenTtl();
    }

    /** Visible for tests asserting on the claim set. */
    static String roleClaim(WorkspaceRole role) {
        return role == null ? null : role.name();
    }
}
