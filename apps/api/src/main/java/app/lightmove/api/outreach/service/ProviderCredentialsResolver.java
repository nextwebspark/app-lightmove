package app.lightmove.api.outreach.service;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The OAuth app a workspace connects a provider through. A workspace with no row, or one on the shared mode,
 * gets Uncava's app; one on its own gets its decrypted keys. Empty when the workspace is on the shared mode and
 * this deployment has no shared app at that provider.
 */
@Service
@RequiredArgsConstructor
public class ProviderCredentialsResolver {

    private final WorkspaceMailIntegrationRepository integrations;
    private final ProviderAppSetup setup;
    private final SecretCipher cipher;

    @Transactional(readOnly = true)
    public Optional<ProviderCredentials> resolve(UUID workspaceId, IntegrationProvider provider) {
        Optional<WorkspaceMailIntegration> own = integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .filter(WorkspaceMailIntegration::isOwnApp);
        if (own.isPresent()) {
            return own.map(this::ownCredentials);
        }
        return setup.sharedApp(provider).map(app -> new ProviderCredentials(
                provider, CredentialMode.SHARED, app.clientId(), app.clientSecret(), null));
    }

    /** Whether anyone on this deployment could connect at {@code provider}: the shared app, or some firm's own. */
    @Transactional(readOnly = true)
    public boolean isAnyAppAt(IntegrationProvider provider) {
        return setup.sharedApp(provider).isPresent()
                || integrations.existsByProviderAndMode(provider, CredentialMode.OWN);
    }

    private ProviderCredentials ownCredentials(WorkspaceMailIntegration integration) {
        String secret = cipher.decrypt(integration.getClientSecretEncrypted(), integration.clientSecretContext());
        return new ProviderCredentials(integration.getProvider(), CredentialMode.OWN, integration.getClientId(),
                secret, integration.getTenantId());
    }
}
