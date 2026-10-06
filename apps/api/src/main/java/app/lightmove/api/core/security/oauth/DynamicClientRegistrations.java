package app.lightmove.api.core.security.oauth;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2ClientRegistration;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/**
 * Spring's RFC 7591 endpoint narrowed through its provider's two hooks. Anyone may register, so the client is public,
 * holds no secret, and is never shown as verified.
 */
final class DynamicClientRegistrations {

    /** Carried from the registration to {@link OAuthRegisteredClients#save}, which keeps it in its own columns. */
    static final String SOURCE_SETTING = "lightmove.client.source";
    static final String CLIENT_URI_SETTING = "lightmove.client.client-uri";
    static final String LOGO_URI_SETTING = "lightmove.client.logo-uri";

    private static final String JWKS = "jwks";
    private static final String LOGO_URI = "logo_uri";
    private static final String CLIENT_URI = "client_uri";

    private DynamicClientRegistrations() {
    }

    static void validate(OAuth2ClientRegistrationAuthenticationContext context) {
        OAuth2ClientRegistrationAuthenticationToken request = context.getAuthentication();
        OAuth2ClientRegistration registration = request.getClientRegistration();
        if (!ClientMetadataRules.isPublicAuthMethod(registration.getTokenEndpointAuthenticationMethod())) {
            throw refusal(ClientMetadataRules.INVALID_CLIENT_METADATA, "token_endpoint_auth_method must be none");
        }
        if (!ClientMetadataRules.isAllowedGrantTypes(registration.getGrantTypes())) {
            throw refusal(ClientMetadataRules.INVALID_CLIENT_METADATA,
                    "grant_types may hold authorization_code and refresh_token only");
        }
        if (!ClientMetadataRules.isAllowedResponseTypes(registration.getResponseTypes())) {
            throw refusal(ClientMetadataRules.INVALID_CLIENT_METADATA, "response_types may hold code only");
        }
        if (registration.getJwkSetUrl() != null || registration.getClaims().containsKey(JWKS)) {
            throw refusal(ClientMetadataRules.INVALID_CLIENT_METADATA, "A public client registers no keys");
        }
        if (!RedirectUriRules.acceptable(registration.getRedirectUris())) {
            throw refusal(ClientMetadataRules.INVALID_REDIRECT_URI,
                    "redirect_uris must be https, or http on 127.0.0.1, [::1] or localhost, with no fragment");
        }
    }

    static RegisteredClient toRegisteredClient(OAuth2ClientRegistration registration, Clock clock) {
        List<String> redirectUris = List.copyOf(registration.getRedirectUris());
        ClientSettings.Builder settings = ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(true)
                .setting(SOURCE_SETTING, OAuthClientSource.DCR.name());
        String clientUri = ClientMetadataRules.httpsUriOrNull(registration.getClaims().get(CLIENT_URI));
        if (clientUri != null) {
            settings.setting(CLIENT_URI_SETTING, clientUri);
        }
        String logoUri = ClientMetadataRules.httpsUriOrNull(registration.getClaims().get(LOGO_URI));
        if (logoUri != null) {
            settings.setting(LOGO_URI_SETTING, logoUri);
        }
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(UUID.randomUUID().toString())
                .clientIdIssuedAt(clock.instant())
                .clientName(ClientMetadataRules.displayNameOf(registration.getClientName(), redirectUris))
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scopes(scopes -> scopes.addAll(ClientMetadataRules.scopesOf(registration.getScopes())))
                .clientSettings(settings.build())
                .build();
    }

    private static OAuth2AuthenticationException refusal(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
