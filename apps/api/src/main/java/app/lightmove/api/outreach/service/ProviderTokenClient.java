package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.RefreshedAccessToken;

/** A provider's OAuth token endpoint, asked for an access token from a stored refresh token. */
public interface ProviderTokenClient {

    /**
     * @throws RefreshTokenRefused when the provider will never honour this refresh token again
     * @throws ProviderAppUnavailable when the provider refused the app itself
     * @throws app.lightmove.api.core.resilience.model.VendorException for anything a later try might get past
     */
    RefreshedAccessToken refresh(ProviderCredentials credentials, String refreshToken);
}
