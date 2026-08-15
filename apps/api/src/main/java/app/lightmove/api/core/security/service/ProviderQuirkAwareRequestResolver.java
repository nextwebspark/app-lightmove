package app.lightmove.api.core.security.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;

/**
 * The standard authorisation request, minus the extensions a given provider does not implement.
 *
 * <p>Spring sends PKCE and a nonce to everyone. A provider that implements neither — LinkedIn
 * implements neither — fails in ways that name the wrong thing: the {@code code_verifier} comes back
 * as {@code invalid_client}, "client authentication failed", about credentials that are perfectly
 * good; the missing nonce comes back as {@code invalid_nonce} only after the exchange has already
 * succeeded. Both cost an afternoon if you believe the message.
 *
 * <p>Which registrations those are is configuration
 * ({@code lightmove.auth.oauth.pkce-unsupported-registrations} and
 * {@code …nonce-unsupported-registrations}) rather than a branch on a provider name, because a
 * provider here is a yml block and hard-coding one is the thing that would undo that.
 */
@Slf4j
public class ProviderQuirkAwareRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final Set<String> registrationsWithoutPkce;
    private final Set<String> registrationsWithoutNonce;

    public ProviderQuirkAwareRequestResolver(ClientRegistrationRepository registrations,
                                                 List<String> registrationsWithoutPkce,
                                                 List<String> registrationsWithoutNonce) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations,
                DefaultOAuth2AuthorizationRequestResolver.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.registrationsWithoutPkce = Set.copyOf(registrationsWithoutPkce);
        this.registrationsWithoutNonce = Set.copyOf(registrationsWithoutNonce);

        if (!this.registrationsWithoutPkce.isEmpty() || !this.registrationsWithoutNonce.isEmpty()) {
            log.info("OAuth registrations without PKCE: {}, without nonce: {}", this.registrationsWithoutPkce,
                    this.registrationsWithoutNonce);
        }
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return stripUnsupportedExtensions(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return stripUnsupportedExtensions(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest stripUnsupportedExtensions(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        // Assigned, not wrapped in String.valueOf: getAttribute is generic, so the inferred T makes
        // valueOf bind to its char[] overload and the call dies with a ClassCastException.
        String registrationId = authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);

        // An immutable Set throws on contains(null) rather than answering false.
        boolean dropPkce = registrationId != null && registrationsWithoutPkce.contains(registrationId);
        boolean dropNonce = registrationId != null && registrationsWithoutNonce.contains(registrationId);
        if (!dropPkce && !dropNonce) {
            return authorizationRequest;
        }

        Map<String, Object> parameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
        Map<String, Object> attributes = new HashMap<>(authorizationRequest.getAttributes());

        if (dropPkce) {
            parameters.remove(PkceParameterNames.CODE_CHALLENGE);
            parameters.remove(PkceParameterNames.CODE_CHALLENGE_METHOD);
            // The verifier is carried as an attribute and replayed at the token exchange, so removing
            // only the challenge would still send it — which is the half the provider rejects.
            attributes.remove(PkceParameterNames.CODE_VERIFIER);
        }
        if (dropNonce) {
            parameters.remove(OidcParameterNames.NONCE);
            // Validation is driven by the stored attribute: leave it and Spring demands the id_token
            // echo a nonce we no longer asked for.
            attributes.remove(OidcParameterNames.NONCE);
        }

        // Rebuilt field by field rather than with from(), which carries the already-rendered
        // authorizationRequestUri across: the parameters would come out clean and the URI the browser
        // actually follows would still carry them.
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationRequest.getAuthorizationUri())
                .clientId(authorizationRequest.getClientId())
                .redirectUri(authorizationRequest.getRedirectUri())
                .scopes(authorizationRequest.getScopes())
                .state(authorizationRequest.getState())
                .additionalParameters(parameters)
                .attributes(attributes)
                .build();
    }
}
