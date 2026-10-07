package app.lightmove.api.core.security.oauth;

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Component;

/**
 * Remembers no consent. The framework's consent provider adds back every scope an earlier consent of the same user to
 * the same client held, so a stored consent would quietly re-grant contacts to a connection the user just unticked
 * them on. What a grant was given lives on the grant itself — its scopes and its workspace — and the consent screen
 * asks again every time.
 */
@Component
public class PerGrantConsentService implements OAuth2AuthorizationConsentService {

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return null;
    }
}
