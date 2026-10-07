package app.lightmove.api.core.security.oauth;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * The signed-in user as the authorization server keeps them: the user id and nothing else. The framework stores the
 * principal inside the grant, through Jackson modules that read back only the types Spring allows, so it is a plain
 * {@link UsernamePasswordAuthenticationToken} rather than the session's own principal, and carries no roles — every
 * decision reads the membership again.
 */
public final class SessionUserAuthentication {

    private SessionUserAuthentication() {
    }

    public static AbstractAuthenticationToken of(Jwt sessionToken) {
        if (!Boolean.TRUE.equals(sessionToken.getClaim("emailVerified"))) {
            throw new InvalidBearerTokenException("An unverified account connects no app");
        }
        return UsernamePasswordAuthenticationToken.authenticated(sessionToken.getSubject(), null, List.of());
    }
}
