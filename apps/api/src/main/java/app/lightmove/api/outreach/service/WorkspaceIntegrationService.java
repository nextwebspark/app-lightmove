package app.lightmove.api.outreach.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationResponse;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationsResponse;
import app.lightmove.api.outreach.model.OwnAppKeys;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Integrations: which OAuth app each provider is connected through. Every change is an audit event
 * of the {@code integrations} section, which names the provider and the mode and never the secret.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceIntegrationService {

    private final WorkspaceMailIntegrationRepository integrations;
    private final ProviderAppSetup setup;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final LightMoveProperties properties;

    @Transactional(readOnly = true)
    public WorkspaceIntegrationsResponse list(UUID workspaceId) {
        Map<IntegrationProvider, WorkspaceMailIntegration> chosen = integrations.findByWorkspaceId(workspaceId)
                .stream()
                .collect(Collectors.toMap(WorkspaceMailIntegration::getProvider, Function.identity()));
        return new WorkspaceIntegrationsResponse(
                Arrays.stream(IntegrationProvider.values()).map(provider -> toDto(provider, chosen.get(provider)))
                        .toList(),
                cipher.isAvailable(),
                isRecallOffered());
    }

    /** {@code PUT}: the shared app takes no keys, so keys sent with it are refused rather than dropped. */
    @Transactional
    public WorkspaceIntegrationsResponse update(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                                CredentialMode mode, OwnAppKeys keys, HttpServletRequest request) {
        if (mode == CredentialMode.OWN) {
            return useOwnApp(actorId, workspaceId, provider, keys, request);
        }
        if (!keys.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "mode", "Keys are only kept for your own app");
        }
        return useSharedApp(actorId, workspaceId, provider, request);
    }

    /** Discards the workspace's own keys. A workspace already on the shared app records nothing. */
    @Transactional
    public WorkspaceIntegrationsResponse useSharedApp(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                                      HttpServletRequest request) {
        integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .filter(WorkspaceMailIntegration::isOwnApp)
                .ifPresent(integration -> {
                    integration.useSharedApp(actorId);
                    recordIntegrationChange(actorId, workspaceId, provider, CredentialMode.SHARED, null, request);
                });
        return list(workspaceId);
    }

    /** A save that changes nothing records nothing, as the shared app and the calendar sync do. */
    private WorkspaceIntegrationsResponse useOwnApp(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                                    OwnAppKeys keys, HttpServletRequest request) {
        if (!cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        WorkspaceMailIntegration integration = integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .orElseGet(() -> WorkspaceMailIntegration.sharedApp(workspaceId, provider));
        String clientId = requireClientId(keys);
        String tenantId = tenantIdFor(provider, keys);
        String newSecret = keys.clientSecret() == null || keys.clientSecret().isBlank() ? null : keys.clientSecret();
        if (newSecret == null && !integration.holdsSecretFor(clientId)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret", "Enter your app's client secret");
        }

        String encryptedSecret = newSecret == null ? null : cipher.encrypt(newSecret, integration.clientSecretContext());
        if (integration.useOwnApp(clientId, encryptedSecret, tenantId, keys.secretExpiresOn(), actorId)) {
            integrations.save(integration);
            recordIntegrationChange(actorId, workspaceId, provider, CredentialMode.OWN, newSecret != null, request);
        }
        return list(workspaceId);
    }

    /** The {@code integrations} section's one shape: the provider and the mode, never a key. */
    private void recordIntegrationChange(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                         CredentialMode mode, Boolean secretChanged, HttpServletRequest request) {
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "integrations")
                .detail("provider", provider.name())
                .detail("mode", mode.name())
                .detailIfPresent("secretChanged", secretChanged)
                .record();
    }

    private WorkspaceIntegrationResponse toDto(IntegrationProvider provider, WorkspaceMailIntegration integration) {
        boolean own = integration != null && integration.isOwnApp();
        String adminConsentUrl = provider == IntegrationProvider.MICROSOFT
                ? setup.microsoftAdminConsentUri().map(URI::toString).orElse(null)
                : null;
        return new WorkspaceIntegrationResponse(
                provider,
                own ? CredentialMode.OWN : CredentialMode.SHARED,
                own ? integration.getClientId() : null,
                own ? integration.getTenantId() : null,
                own ? integration.getSecretExpiresOn() : null,
                own && integration.hasSecret(),
                setup.sharedApp(provider).isPresent(),
                setup.redirectUri(provider).toString(),
                setup.scopes(provider),
                adminConsentUrl,
                setup.ownAppGuideUrl(provider).orElse(null),
                setup.sharedAppGuideUrl(provider).orElse(null),
                integration == null ? null : integration.getUpdatedAt());
    }

    private static String requireClientId(OwnAppKeys keys) {
        String clientId = blankToNull(keys.clientId());
        if (clientId == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientId", "Enter your app's client ID");
        }
        return clientId;
    }

    /** A single-tenant Entra app signs in at its own directory, so Microsoft needs one; nobody else has any. */
    private static String tenantIdFor(IntegrationProvider provider, OwnAppKeys keys) {
        String tenantId = blankToNull(keys.tenantId());
        if (provider == IntegrationProvider.MICROSOFT && tenantId == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "tenantId", "Enter your directory's tenant ID");
        }
        if (provider != IntegrationProvider.MICROSOFT && tenantId != null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "tenantId", "Only a Microsoft app has a tenant ID");
        }
        return tenantId;
    }

    private boolean isRecallOffered() {
        RecallSettings recall = properties.recall();
        return recall != null && recall.isConfigured();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
