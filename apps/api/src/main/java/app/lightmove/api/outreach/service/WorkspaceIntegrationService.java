package app.lightmove.api.outreach.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ProviderAppSettings;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationResponse;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationsResponse;
import app.lightmove.api.outreach.model.OwnAppKeys;
import app.lightmove.api.outreach.model.ProviderAppKeysReplaced;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Integrations: which OAuth app each provider is connected through. Every change is an audit event
 * of the {@code integrations} section, which names the provider and the mode and never the secret. A change of app
 * withdraws every connection made through the old one, since its refresh tokens cannot be refreshed through the new
 * one; new keys for the same app are handed to Recall once they commit.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceIntegrationService {

    private static final Pattern GUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** A verified domain of the directory; `common`, `organizations` and `consumers` have no dot and fail it. */
    private static final Pattern DIRECTORY_DOMAIN = Pattern.compile("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");
    private static final String GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com";
    /** Entra's ceiling on a client secret's lifetime. */
    private static final Period LONGEST_MICROSOFT_SECRET = Period.ofMonths(24);

    private final WorkspaceMailIntegrationRepository integrations;
    private final ProviderAppSetup setup;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;
    private final ReplacedAppConnections connections;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public WorkspaceIntegrationsResponse list(UUID workspaceId) {
        Map<IntegrationProvider, WorkspaceMailIntegration> chosen = integrations.findByWorkspaceId(workspaceId)
                .stream()
                .collect(Collectors.toMap(WorkspaceMailIntegration::getProvider, Function.identity()));
        return new WorkspaceIntegrationsResponse(
                Arrays.stream(IntegrationProvider.values())
                        .map(provider -> toDto(workspaceId, provider, chosen.get(provider)))
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
                    String replacedClientId = integration.getClientId();
                    integration.useSharedApp(actorId);
                    int withdrawn = withdrawUnlessSameApp(workspaceId, provider, replacedClientId,
                            sharedClientId(provider));
                    recordIntegrationChange(actorId, workspaceId, provider, CredentialMode.SHARED, null, withdrawn,
                            request);
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
        String clientId = requireClientId(provider, keys);
        String tenantId = tenantIdFor(provider, keys);
        String newSecret = secretOf(provider, keys);
        if (newSecret == null && !integration.holdsSecretFor(clientId)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret", "Enter your app's client secret");
        }
        LocalDate secretExpiresOn = expiryOf(provider, keys, integration);
        String previousClientId = integration.isOwnApp() ? integration.getClientId() : sharedClientId(provider);
        boolean keysReplaced = newSecret != null || !Objects.equals(integration.getTenantId(), tenantId);

        String encryptedSecret = newSecret == null ? null : cipher.encrypt(newSecret, integration.clientSecretContext());
        if (integration.useOwnApp(clientId, encryptedSecret, tenantId, secretExpiresOn, actorId)) {
            integrations.save(integration);
            int withdrawn = withdrawUnlessSameApp(workspaceId, provider, previousClientId, clientId);
            if (clientId.equals(previousClientId) && keysReplaced) {
                events.publishEvent(new ProviderAppKeysReplaced(workspaceId, provider));
            }
            recordIntegrationChange(actorId, workspaceId, provider, CredentialMode.OWN, newSecret != null, withdrawn,
                    request);
        }
        return list(workspaceId);
    }

    /**
     * Microsoft sent an admin back from the admin-consent link with the directory they approved Uncava's shared app
     * for. Recorded so the page can say so, and only when it returns the state our link carried.
     */
    @Transactional
    public WorkspaceIntegrationsResponse recordMicrosoftAdminConsent(UUID actorId, UUID workspaceId, String tenantId,
                                                                     String state, HttpServletRequest request) {
        if (setup.sharedApp(IntegrationProvider.MICROSOFT).isEmpty()) {
            throw ApiException.of(ErrorCode.INTEGRATION_SHARED_APP_UNAVAILABLE);
        }
        if (!setup.isAdminConsentState(workspaceId, state)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "state",
                    "This approval did not come from your workspace's link");
        }
        WorkspaceMailIntegration integration = integrations
                .findByWorkspaceIdAndProvider(workspaceId, IntegrationProvider.MICROSOFT)
                .orElseGet(() -> WorkspaceMailIntegration.sharedApp(workspaceId, IntegrationProvider.MICROSOFT));
        integration.recordAdminConsent(tenantId, actorId, clock.instant());
        integrations.save(integration);
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "integrations")
                .detail("provider", IntegrationProvider.MICROSOFT.name())
                .detail("adminConsentTenant", tenantId)
                .record();
        return list(workspaceId);
    }

    /** The {@code integrations} section's one shape: the provider and the mode, never a key. */
    private void recordIntegrationChange(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                         CredentialMode mode, Boolean secretChanged, int connectionsWithdrawn,
                                         HttpServletRequest request) {
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "integrations")
                .detail("provider", provider.name())
                .detail("mode", mode.name())
                .detailIfPresent("secretChanged", secretChanged)
                .detailIfPresent("connectionsWithdrawn", connectionsWithdrawn == 0 ? null : connectionsWithdrawn)
                .record();
    }

    /** Connections made through the app being left cannot be refreshed through the next one. */
    private int withdrawUnlessSameApp(UUID workspaceId, IntegrationProvider provider, String previousClientId,
                                      String nextClientId) {
        return Objects.equals(previousClientId, nextClientId) ? 0
                : connections.withdrawThrough(workspaceId, provider, previousClientId);
    }

    private String sharedClientId(IntegrationProvider provider) {
        return setup.sharedApp(provider).map(ProviderAppSettings::clientId).orElse(null);
    }

    private WorkspaceIntegrationResponse toDto(UUID workspaceId, IntegrationProvider provider,
                                               WorkspaceMailIntegration integration) {
        boolean own = integration != null && integration.isOwnApp();
        String appClientId = own ? integration.getClientId() : sharedClientId(provider);
        String adminConsentUrl = provider == IntegrationProvider.MICROSOFT
                ? setup.microsoftAdminConsentUri(workspaceId).map(URI::toString).orElse(null)
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
                integration == null ? null : integration.getUpdatedAt(),
                integration == null ? null : integration.getAdminConsentedAt(),
                integration == null ? null : integration.getAdminConsentTenantId(),
                connections.countThrough(workspaceId, provider, appClientId));
    }

    /** Each provider issues its client ids in one shape; another is a paste of the wrong value. */
    private static String requireClientId(IntegrationProvider provider, OwnAppKeys keys) {
        String clientId = blankToNull(keys.clientId());
        if (clientId == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientId", "Enter your app's client ID");
        }
        if (provider == IntegrationProvider.GOOGLE && !clientId.endsWith(GOOGLE_CLIENT_ID_SUFFIX)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientId",
                    "A Google client ID ends in .apps.googleusercontent.com");
        }
        if (provider == IntegrationProvider.MICROSOFT && !GUID.matcher(clientId).matches()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientId",
                    "Paste the application (client) ID from the app's Overview");
        }
        return clientId;
    }

    /**
     * A single-tenant Entra app signs in at its own directory, so Microsoft needs one — its id or a verified domain,
     * never a multi-tenant alias like {@code common}; nobody else has any.
     */
    private static String tenantIdFor(IntegrationProvider provider, OwnAppKeys keys) {
        String tenantId = blankToNull(keys.tenantId());
        if (provider == IntegrationProvider.MICROSOFT && tenantId == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "tenantId", "Enter your directory's tenant ID");
        }
        if (provider != IntegrationProvider.MICROSOFT && tenantId != null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "tenantId", "Only a Microsoft app has a tenant ID");
        }
        if (tenantId != null && !GUID.matcher(tenantId).matches() && !DIRECTORY_DOMAIN.matcher(tenantId).matches()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "tenantId",
                    "Enter your directory's ID or primary domain");
        }
        return tenantId;
    }

    /**
     * Null keeps the secret already held. Never trimmed — whitespace inside a secret is the secret — but no provider
     * issues one with a space or a line break, so a secret carrying one is a paste that took more than the value.
     */
    private static String secretOf(IntegrationProvider provider, OwnAppKeys keys) {
        String secret = keys.clientSecret();
        if (secret == null || secret.isBlank()) {
            return null;
        }
        if (secret.codePoints().anyMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point)
                || Character.isISOControl(point))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret",
                    "A client secret has no spaces or line breaks. Copy its value alone");
        }
        if (provider == IntegrationProvider.MICROSOFT && GUID.matcher(secret).matches()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret",
                    "That is the secret's ID. Paste its value instead");
        }
        return secret;
    }

    /** Only an Entra secret expires, within 24 months; a date already on the row stands, so a lapsed one re-saves. */
    private LocalDate expiryOf(IntegrationProvider provider, OwnAppKeys keys, WorkspaceMailIntegration integration) {
        LocalDate expiresOn = keys.secretExpiresOn();
        if (provider != IntegrationProvider.MICROSOFT) {
            if (expiresOn != null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "secretExpiresOn",
                        "Only a Microsoft secret expires");
            }
            return null;
        }
        if (expiresOn == null || expiresOn.equals(integration.getSecretExpiresOn())) {
            return expiresOn;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (expiresOn.isBefore(today)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "secretExpiresOn", "That date has passed");
        }
        if (expiresOn.isAfter(today.plus(LONGEST_MICROSOFT_SECRET))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "secretExpiresOn",
                    "A Microsoft secret lasts 24 months at most");
        }
        return expiresOn;
    }

    private boolean isRecallOffered() {
        RecallSettings recall = properties.recall();
        return recall != null && recall.isCalendarOffered();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
