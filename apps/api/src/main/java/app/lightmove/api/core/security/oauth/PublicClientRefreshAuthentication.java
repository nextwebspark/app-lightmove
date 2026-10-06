package app.lightmove.api.core.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Client authentication for a public client's refresh request, which carries only its {@code client_id}. The
 * framework authenticates a public client solely on the code grant, by its PKCE verifier; without this a public
 * client's refresh token could never be spent. The refresh token itself, single-use and rotated, is the proof.
 */
public final class PublicClientRefreshAuthentication {

    private PublicClientRefreshAuthentication() {
    }

    public static final class Converter implements AuthenticationConverter {

        private final String tokenEndpoint;

        /**
         * The client authentication filter serves revocation and introspection too; there the framework looks a token up
         * by its raw value, which a hashed store cannot answer, so a public client is authenticated on the token endpoint
         * and nowhere else.
         */
        public Converter(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
        }

        @Override
        public Authentication convert(HttpServletRequest request) {
            if (!HttpMethod.POST.matches(request.getMethod())
                    || !tokenEndpoint.equals(request.getRequestURI())
                    || !AuthorizationGrantType.REFRESH_TOKEN.getValue()
                    .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                    || request.getHeader(HttpHeaders.AUTHORIZATION) != null
                    || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null
                    || request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null) {
                return null;
            }
            String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
            if (clientIds == null || clientIds.length != 1 || clientIds[0].isBlank()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
            }
            Map<String, Object> additionalParameters = new HashMap<>();
            request.getParameterMap().forEach((name, values) -> {
                if (!OAuth2ParameterNames.CLIENT_ID.equals(name)) {
                    additionalParameters.put(name, values.length == 1 ? values[0] : values);
                }
            });
            return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null,
                    additionalParameters);
        }
    }

    public static final class Provider implements AuthenticationProvider {

        private final RegisteredClientRepository clients;

        public Provider(RegisteredClientRepository clients) {
            this.clients = clients;
        }

        @Override
        public Authentication authenticate(Authentication authentication) {
            OAuth2ClientAuthenticationToken client = (OAuth2ClientAuthenticationToken) authentication;
            if (!ClientAuthenticationMethod.NONE.equals(client.getClientAuthenticationMethod())
                    || !AuthorizationGrantType.REFRESH_TOKEN.getValue()
                    .equals(client.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE))) {
                return null;
            }
            RegisteredClient registered = clients.findByClientId((String) client.getPrincipal());
            if (registered == null
                    || !registered.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                    || !registered.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            }
            return new OAuth2ClientAuthenticationToken(registered, ClientAuthenticationMethod.NONE, null);
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
