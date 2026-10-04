package app.lightmove.api.outreach.service;

/**
 * The provider refused the grant itself ({@code invalid_grant}): a refresh token revoked, expired or with consent
 * withdrawn, or a consent screen's code already spent. Trying the same grant again never works; a refused refresh
 * token needs the consultant to reconnect.
 */
public class ProviderGrantRefused extends RuntimeException {

    public ProviderGrantRefused(String providerError) {
        super(providerError);
    }
}
