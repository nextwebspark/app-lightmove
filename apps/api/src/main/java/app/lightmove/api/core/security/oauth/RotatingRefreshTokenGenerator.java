package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.security.token.Tokens;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Refresh tokens for public clients. The framework's own generator issues none to a public client unless it proves
 * possession with DPoP, which no MCP client does; OAuth 2.1 allows one where every use rotates it, which
 * {@code reuseRefreshTokens=false} and the replay check in {@link HashingAuthorizationService} make so.
 */
public class RotatingRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final Clock clock;

    public RotatingRefreshTokenGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())
                || !context.getRegisteredClient().getAuthorizationGrantTypes()
                .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            return null;
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(Tokens.generate(), issuedAt, expiresAt);
    }
}
