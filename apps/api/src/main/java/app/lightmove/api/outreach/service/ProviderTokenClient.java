package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.RefreshedAccessToken;
import java.net.URI;
import java.util.List;

/** A provider's OAuth token endpoint: a consent screen's code redeemed, and a stored refresh token spent. */
public interface ProviderTokenClient {

    /**
     * @throws RefreshTokenRefused when the provider will never honour this refresh token again
     * @throws ProviderAppUnavailable when the provider refused the app itself
     * @throws app.lightmove.api.core.resilience.model.VendorException for anything a later try might get past
     */
    RefreshedAccessToken refresh(ProviderCredentials credentials, String refreshToken);

    /**
     * Redeems the code a consent screen sent back for an access token and a refresh token. Never retried: a code is
     * single-use. {@code scopes} is sent where the provider asks for it again (Microsoft); empty sends none.
     */
    RefreshedAccessToken redeemCode(ProviderCredentials credentials, String code, URI redirectUri, List<String> scopes);
}
