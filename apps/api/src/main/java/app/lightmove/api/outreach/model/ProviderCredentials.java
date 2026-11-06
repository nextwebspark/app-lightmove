package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;

/**
 * The OAuth app a workspace connects one provider through, secret decrypted. Held only for the call that needs
 * it; {@link #toString()} leaves the secret out so a stray log line cannot carry it.
 *
 * @param tenantId the customer's Entra directory, on Microsoft's own-app mode only; null otherwise
 * @param keysRefused the workspace's own app was refused the last time the provider was asked
 */
public record ProviderCredentials(
        IntegrationProvider provider,
        CredentialMode mode,
        String clientId,
        String clientSecret,
        String tenantId,
        boolean keysRefused
) {

    public ProviderCredentials(IntegrationProvider provider, CredentialMode mode, String clientId, String clientSecret,
                               String tenantId) {
        this(provider, mode, clientId, clientSecret, tenantId, false);
    }

    @Override
    public String toString() {
        return "ProviderCredentials[provider=" + provider + ", mode=" + mode + ", clientId=" + clientId
                + ", tenantId=" + tenantId + ", keysRefused=" + keysRefused + ", clientSecret=<redacted>]";
    }
}
