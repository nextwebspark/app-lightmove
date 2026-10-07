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
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Authenticates a public client's refresh and revocation requests by {@code client_id} alone, which the framework does
 * only for the code grant; the token presented is the proof, and a revocation ends only the client's own grant.
 */
public final class PublicClientAuthentication {

    private static final String REVOKED_TOKEN = "token";

    private PublicClientAuthentication() {
    }

    public static final class Converter implements AuthenticationConverter {

        private final String tokenEndpoint;
        private final String revocationEndpoint;

        /**
         * The client authentication filter serves introspection too, where the framework looks a token up by its raw
         * value, which a hashed store cannot answer: a public client is authenticated on these two endpoints only.
         */
        public Converter(String tokenEndpoint, String revocationEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            this.revocationEndpoint = revocationEndpoint;
        }

        @Override
        public Authentication convert(HttpServletRequest request) {
            if (!HttpMethod.POST.matches(request.getMethod()) || !(isRefresh(request) || isRevocation(request))
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

        private boolean isRefresh(HttpServletRequest request) {
            return tokenEndpoint.equals(request.getRequestURI()) && AuthorizationGrantType.REFRESH_TOKEN.getValue()
                    .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
        }

        private boolean isRevocation(HttpServletRequest request) {
            return revocationEndpoint.equals(request.getRequestURI()) && request.getParameter(REVOKED_TOKEN) != null
                    && request.getParameter(OAuth2ParameterNames.GRANT_TYPE) == null;
        }
    }

    public static final class Provider implements AuthenticationProvider {

        private final ClientMetadataDocumentClients clients;

        public Provider(ClientMetadataDocumentClients clients) {
            this.clients = clients;
        }

        @Override
        public Authentication authenticate(Authentication authentication) {
            OAuth2ClientAuthenticationToken client = (OAuth2ClientAuthenticationToken) authentication;
            Object grantType = client.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE);
            boolean refresh = AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType);
            boolean revocation = grantType == null && client.getAdditionalParameters().containsKey(REVOKED_TOKEN);
            if (!ClientAuthenticationMethod.NONE.equals(client.getClientAuthenticationMethod())
                    || client.getCredentials() != null || !(refresh || revocation)) {
                return null;
            }
            String clientId = (String) client.getPrincipal();
            RegisteredClient registered = refresh ? clients.findByClientId(clientId)
                    : clients.findStoredByClientId(clientId);
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

    /**
     * Spring's public client authentication, held to the code grant it exists for. Left to see every secretless request,
     * it looked up the client of a revocation too — fetching a metadata document the revocation path must never fetch.
     */
    public static final class CodeGrantOnly implements AuthenticationProvider {

        private final AuthenticationProvider framework;

        public CodeGrantOnly(AuthenticationProvider framework) {
            this.framework = framework;
        }

        @Override
        public Authentication authenticate(Authentication authentication) {
            return authentication instanceof OAuth2ClientAuthenticationToken client
                    && client.getAdditionalParameters().containsKey(PkceParameterNames.CODE_VERIFIER)
                    ? framework.authenticate(authentication)
                    : null;
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return framework.supports(authentication);
        }
    }
}
