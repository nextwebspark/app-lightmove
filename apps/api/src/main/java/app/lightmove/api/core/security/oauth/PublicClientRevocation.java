package app.lightmove.api.core.security.oauth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;

/**
 * Token revocation (RFC 7009) in place of the framework's, which reads the presented token back out of the grant by its
 * value — never there, since only its hash is stored. Revoking either token ends the whole grant: a client revokes when
 * someone disconnects it, and a grant with its refresh token gone has nothing left to do.
 */
public class PublicClientRevocation implements AuthenticationProvider {

    private final HashingAuthorizationService authorizations;

    public PublicClientRevocation(HashingAuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2TokenRevocationAuthenticationToken revocation = (OAuth2TokenRevocationAuthenticationToken) authentication;
        if (!(revocation.getPrincipal() instanceof OAuth2ClientAuthenticationToken client) || !client.isAuthenticated()
                || client.getRegisteredClient() == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        OAuth2Authorization grant = authorizations.findByToken(revocation.getToken(), null);
        if (grant == null) {
            // An unknown or already revoked token is answered as revoked (RFC 7009 §2.2).
            return revocation;
        }
        if (!client.getRegisteredClient().getId().equals(grant.getRegisteredClientId())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        authorizations.revokeAtClientRequest(grant);
        return revocation;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenRevocationAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
