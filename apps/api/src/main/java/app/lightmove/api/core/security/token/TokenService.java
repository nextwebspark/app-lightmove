package app.lightmove.api.core.security.token;
import app.lightmove.api.core.security.model.AuthenticatedSession;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.AuthSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.service.ClientIpResolver;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues, rotates and revokes sessions: access-token claims, refresh rotation, and replay detection. */
@Service
@Slf4j
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final ClientIpResolver clientIpResolver;
    private final AuthSettings config;

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens,
                        AuditService audit, ClientIpResolver clientIpResolver, LightMoveProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clientIpResolver = clientIpResolver;
        this.config = properties.auth();
    }

    @Transactional
    public AuthenticatedSession issue(User user, WorkspaceMember membership, HttpServletRequest request) {
        return issue(user, membership, request, SessionClient.WEB_APP);
    }

    /** The same, for a named client: the extension's token comes back in the body and lives its own TTL. */
    @Transactional
    public AuthenticatedSession issue(User user, WorkspaceMember membership, HttpServletRequest request,
                                      SessionClient client) {
        Instant now = Instant.now();

        String plaintext = Tokens.generate();
        RefreshToken token = RefreshToken.issue(
                user.getId(),
                client,
                Tokens.hash(plaintext),
                now.plus(refreshTokenTtl(client)),
                userAgent(request),
                clientIpResolver.resolve(request));
        refreshTokens.save(token);

        TokenPair pair = new TokenPair(mintAccessToken(user, membership, now), config.accessTokenTtl(), plaintext);
        return new AuthenticatedSession(pair, user, membership);
    }

    /**
     * Redeems a refresh token and burns it; a rotated-away token reappearing revokes the whole family.
     *
     * <p><b>{@code noRollbackFor = ApiException.class} makes the revocation stick:</b> otherwise
     * {@link #handleReuse} throws and the revocation rolls back, leaving every stolen token working.
     *
     * @param membershipLookup re-read at every refresh, so a role change takes effect then
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession rotate(String presentedToken, HttpServletRequest request,
                                       UserLookup userLookup, MembershipLookup membershipLookup) {
        return rotate(presentedToken, request, userLookup, membershipLookup, SessionClient.WEB_APP);
    }

    /** The same, for a named client, which must travel with the rotation or the successor loses its TTL. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession rotate(String presentedToken, HttpServletRequest request,
                                       UserLookup userLookup, MembershipLookup membershipLookup,
                                       SessionClient client) {
        Instant now = Instant.now();
        String presentedHash = Tokens.hash(presentedToken);

        // FOR UPDATE: two concurrent refreshes would both read it un-revoked and reuse detection would
        // never fire — racing the victim is the attack; the lock makes the loser read it revoked.
        RefreshToken existing = refreshTokens.findByTokenHashForUpdate(presentedHash)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID,
                        "No refresh token matches the presented hash"));

        // Both directions: a web cookie token redeemed at /auth/extension/refresh would return its
        // successor in a plaintext body, laundering a credential kept out of script's reach.
        if (existing.getClient() != client) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID,
                    "Refresh token belongs to a different client than " + client);
        }

        if (existing.isRevoked()) {
            // Why, not merely that: calling a logout's dead token theft alarmed the user and burned the
            // reuse alert on routine events. A null reason is unexplained, so it fails closed.
            RevokeReason reason = existing.getRevokedReason();
            if (reason == null || reason.indicatesTheftOnReplay()) {
                handleReuse(existing, request, now);
            }
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token was revoked: " + reason);
        }

        if (existing.isExpired(now)) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token expired");
        }

        User user = userLookup.byId(existing.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "User no longer exists"));

        if (!user.getStatus().canAuthenticate()) {
            refreshTokens.revokeFamily(existing.getFamilyId(), RevokeReason.ADMIN_REVOKED, now);
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "User cannot authenticate: " + user.getStatus());
        }

        String plaintext = Tokens.generate();
        RefreshToken successor = RefreshToken.issueInFamily(
                user.getId(),
                existing.getClient(),
                Tokens.hash(plaintext),
                existing.getFamilyId(),
                now.plus(refreshTokenTtl(client)),
                userAgent(request),
                clientIpResolver.resolve(request));
        refreshTokens.save(successor);

        existing.rotateTo(successor, now);

        audit.event(AuthEventType.TOKEN_REFRESHED).actor(user.getId()).from(request).record();

        WorkspaceMember membership = membershipLookup.forUser(user.getId()).orElse(null);
        TokenPair pair = new TokenPair(mintAccessToken(user, membership, now), config.accessTokenTtl(), plaintext);
        return new AuthenticatedSession(pair, user, membership);
    }

    @Transactional
    public void revoke(String presentedToken, HttpServletRequest request) {
        revoke(presentedToken, request, null);
    }

    /** Ends one session, refusing a family opened for a different client — the fence {@code rotate} applies. */
    @Transactional
    public void revoke(String presentedToken, HttpServletRequest request, SessionClient client) {
        refreshTokens.findByTokenHash(Tokens.hash(presentedToken)).ifPresent(token -> {
            if (client != null && token.getClient() != client) {
                return;
            }
            // Otherwise a token already killed (SUPERSEDED, a password change) records a LOGOUT that never happened.
            if (token.isRevoked()) {
                return;
            }
            token.revoke(RevokeReason.LOGOUT, Instant.now());
            audit.event(AuthEventType.LOGOUT).actor(token.getUserId()).from(request).record();
        });
    }

    @Transactional
    public void revokeAllSessions(UUID userId, RevokeReason reason) {
        int revoked = refreshTokens.revokeAllForUser(userId, reason, Instant.now());
        log.debug("Revoked {} session(s) for user {} ({})", revoked, userId, reason);
    }

    @Transactional
    public void revokeSessionsForClient(UUID userId, SessionClient client, RevokeReason reason) {
        int revoked = refreshTokens.revokeAllForUserAndClient(userId, client, reason, Instant.now());
        log.debug("Revoked {} {} session(s) for user {} ({})", revoked, client, userId, reason);
    }

    private void handleReuse(RefreshToken replayed, HttpServletRequest request, Instant now) {
        int killed = refreshTokens.revokeFamily(replayed.getFamilyId(), RevokeReason.REUSE_DETECTED, now);

        // The one event that should page a human: a refresh token leaked.
        log.warn("Refresh token reuse detected for user {} — revoked {} token(s) in family {}",
                replayed.getUserId(), killed, replayed.getFamilyId());

        audit.event(AuthEventType.TOKEN_REUSE_DETECTED)
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
     * The RS256 access token. {@code wsId} and {@code roles} are absent before onboarding; {@code roles}
     * can be stale for {@code accessTokenTtl}, which is why no decision trusts it — the rbac guard beans
     * re-read the database.
     */
    private String mintAccessToken(User user, WorkspaceMember membership, Instant now) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(config.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(config.accessTokenTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("emailVerified", user.isEmailVerified())
                .id(UUID.randomUUID().toString());

        if (membership != null && membership.isActive()) {
            claims.claim("wsId", membership.getWorkspaceId().toString());
            claims.claim("roles", membership.getRoles().stream()
                    .map(app.lightmove.api.core.security.rbac.Role::getName)
                    .sorted()
                    .toList());
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private Duration refreshTokenTtl(SessionClient client) {
        return client == SessionClient.BROWSER_EXTENSION
                ? config.extension().refreshTokenTtl()
                : config.refreshTokenTtl();
    }

    private static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader("User-Agent");
        return value != null && value.length() > 512 ? value.substring(0, 512) : value;
    }

    /** Passed in rather than injected, which also breaks a cycle with AuthenticationService. */
    @FunctionalInterface
    public interface UserLookup {
        java.util.Optional<User> byId(UUID userId);
    }

    @FunctionalInterface
    public interface MembershipLookup {
        java.util.Optional<WorkspaceMember> forUser(UUID userId);
    }

    public java.time.Duration refreshTokenTtl() {
        return config.refreshTokenTtl();
    }
}
