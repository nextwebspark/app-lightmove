package app.lightmove.api.outreach.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ProviderAppSettings;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationResponse;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationsResponse;
import app.lightmove.api.outreach.model.AppKeysVerdict;
import app.lightmove.api.outreach.model.OwnAppKeys;
import app.lightmove.api.outreach.model.ProviderAppKeysReplaced;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Settings → Integrations: which OAuth app each provider is connected through. Every change is an audit event
 * of the {@code integrations} section, which names the provider and the mode and never the secret. An own app's keys
 * are saved only once the provider accepts them, asked outside any transaction. A change of app withdraws every
 * connection made through the old one, since its refresh tokens cannot be refreshed through the new one; new keys for
 * the same app are handed to Recall once they commit.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkspaceIntegrationService {

    private static final Pattern GUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** A verified domain of the directory; `common`, `organizations` and `consumers` have no dot and fail it. */
    private static final Pattern DIRECTORY_DOMAIN = Pattern.compile("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");
    private static final String GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com";
    /** Entra's ceiling on a client secret's lifetime. */
    private static final Period LONGEST_MICROSOFT_SECRET = Period.ofMonths(24);
    /** Each check is a call to the provider's token endpoint, so a workspace asks at most this many a minute. */
    private static final int KEY_CHECKS_PER_MINUTE = 10;
    /** A secret shorter than this would show too much of itself in its last four characters, so it gets no hint. */
    private static final int SHORTEST_HINTED_SECRET = 16;
    private static final int HINT_LENGTH = 4;

    private final WorkspaceMailIntegrationRepository integrations;
    private final ProviderAppSetup setup;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;
    private final ReplacedAppConnections connections;
    private final ApplicationEventPublisher events;
    private final ProviderTokenClient tokenClient;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public WorkspaceIntegrationsResponse list(UUID workspaceId) {
        List<WorkspaceMailIntegration> rows = integrations.findByWorkspaceId(workspaceId);
        Map<IntegrationProvider, WorkspaceMailIntegration> chosen = rows.stream()
                .collect(Collectors.toMap(WorkspaceMailIntegration::getProvider, Function.identity()));
        Map<UUID, String> saverNames = users.findAllById(rows.stream()
                        .map(WorkspaceMailIntegration::getSecretSavedBy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> Objects.toString(user.getFullName(), user.getEmail())));
        return new WorkspaceIntegrationsResponse(
                Arrays.stream(IntegrationProvider.values())
                        .map(provider -> toDto(workspaceId, provider, chosen.get(provider), saverNames))
                        .toList(),
                cipher.isAvailable(),
                isRecallOffered());
    }

    /** {@code PUT}: the shared app takes no keys, so keys sent with it are refused rather than dropped. */
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
    public WorkspaceIntegrationsResponse useSharedApp(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                                      HttpServletRequest request) {
        transactions.executeWithoutResult(status -> integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .filter(WorkspaceMailIntegration::isOwnApp)
                .ifPresent(integration -> {
                    String replacedClientId = integration.getClientId();
                    integration.useSharedApp(actorId);
                    int withdrawn = withdrawUnlessSameApp(workspaceId, provider, replacedClientId,
                            sharedClientId(provider));
                    recordIntegrationChange(actorId, workspaceId, provider, CredentialMode.SHARED, null, withdrawn,
                            request);
                }));
        return list(workspaceId);
    }

    /**
     * Verify again: the provider is asked about the keys on file, and its answer is kept either way — unless the
     * secret was replaced meanwhile, when the answer is about keys nobody holds any more.
     */
    public WorkspaceIntegrationsResponse verify(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                                HttpServletRequest request) {
        if (!cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        WorkspaceMailIntegration integration = integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .filter(held -> held.isOwnApp() && held.hasSecret())
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Only your own app's keys can be checked"));
        String secret = cipher.decrypt(integration.getClientSecretEncrypted(), integration.clientSecretContext());
        AppKeysVerdict verdict = askProvider(workspaceId, provider, integration.getClientId(), secret,
                integration.getTenantId());
        integrations.recordKeysCheck(integration.getId(), integration.getClientSecretEncrypted(), clock.instant(),
                verdict.refusedWith());
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "integrations")
                .detail("provider", provider.name())
                .detail("keysChecked", verdict.accepted() ? "ACCEPTED" : "REFUSED")
                .detailIfPresent("providerError", verdict.refusedWith())
                .record();
        return list(workspaceId);
    }

    /** Save and verify. A save that changes nothing records nothing, as the shared app and the calendar sync do. */
    private WorkspaceIntegrationsResponse useOwnApp(UUID actorId, UUID workspaceId, IntegrationProvider provider,
                                                    OwnAppKeys keys, HttpServletRequest request) {
        if (!cipher.isAvailable()) {
            throw ApiException.of(ErrorCode.INTEGRATION_ENCRYPTION_UNAVAILABLE);
        }
        WorkspaceMailIntegration held = integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .orElseGet(() -> WorkspaceMailIntegration.sharedApp(workspaceId, provider));
        String clientId = requireClientId(provider, keys);
        String tenantId = tenantIdFor(provider, keys);
        String newSecret = secretOf(provider, keys);
        if (newSecret == null && !held.holdsSecretFor(clientId)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret", "Enter your app's client secret");
        }
        LocalDate secretExpiresOn = expiryOf(provider, keys, held, clientId, newSecret);
        String checkedHeldSecret = newSecret == null ? held.getClientSecretEncrypted() : null;
        String secret = newSecret != null ? newSecret
                : cipher.decrypt(checkedHeldSecret, held.clientSecretContext());

        AppKeysVerdict verdict = askProvider(workspaceId, provider, clientId, secret, tenantId);
        if (!verdict.accepted()) {
            throw ApiException.withProperty(ErrorCode.INTEGRATION_KEYS_REFUSED, "providerError", verdict.refusedWith());
        }
        transactions.executeWithoutResult(status -> saveOwnApp(actorId, workspaceId, provider,
                new AcceptedKeys(clientId, tenantId, newSecret, checkedHeldSecret, secretExpiresOn), request));
        return list(workspaceId);
    }

    private void saveOwnApp(UUID actorId, UUID workspaceId, IntegrationProvider provider, AcceptedKeys keys,
                            HttpServletRequest request) {
        WorkspaceMailIntegration integration = integrations.findByWorkspaceIdAndProvider(workspaceId, provider)
                .orElseGet(() -> WorkspaceMailIntegration.sharedApp(workspaceId, provider));
        if (keys.newSecret() == null && !(integration.holdsSecretFor(keys.clientId())
                && Objects.equals(integration.getClientSecretEncrypted(), keys.checkedHeldSecret()))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret",
                    "The saved secret changed while it was checked. Enter it again");
        }
        String previousClientId = integration.isOwnApp() ? integration.getClientId() : sharedClientId(provider);
        boolean keysReplaced = keys.newSecret() != null || !Objects.equals(integration.getTenantId(), keys.tenantId());
        String encryptedSecret = keys.newSecret() == null ? null
                : cipher.encrypt(keys.newSecret(), integration.clientSecretContext());

        boolean changed = integration.useOwnApp(keys.clientId(), encryptedSecret, keys.tenantId(),
                keys.secretExpiresOn(), actorId);
        if (keys.newSecret() != null) {
            integration.recordNewSecret(hintOf(keys.newSecret()), actorId, clock.instant());
        }
        integration.recordKeysCheck(clock.instant(), null);
        integrations.save(integration);
        if (changed) {
            int withdrawn = withdrawUnlessSameApp(workspaceId, provider, previousClientId, keys.clientId());
            if (keys.clientId().equals(previousClientId) && keysReplaced) {
                events.publishEvent(new ProviderAppKeysReplaced(workspaceId, provider));
            }
            recordIntegrationChange(actorId, workspaceId, provider, CredentialMode.OWN, keys.newSecret() != null,
                    withdrawn, request);
        }
    }

    /** Keys the provider accepted, carried into the write; the held secret's ciphertext is what was checked. */
    private record AcceptedKeys(String clientId, String tenantId, String newSecret, String checkedHeldSecret,
                                LocalDate secretExpiresOn) {

        @Override
        public String toString() {
            return "AcceptedKeys[clientId=" + clientId + ", newSecret=" + (newSecret == null ? "null" : "[redacted]")
                    + "]";
        }
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

    /** Each check spends the workspace's budget; a provider that gives no answer saves and records nothing. */
    private AppKeysVerdict askProvider(UUID workspaceId, IntegrationProvider provider, String clientId, String secret,
                                       String tenantId) {
        if (!rateLimiter.tryAcquire("integration-key-check:workspace:%s".formatted(workspaceId),
                KEY_CHECKS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
        ProviderCredentials app = new ProviderCredentials(provider, CredentialMode.OWN, clientId, secret, tenantId);
        try {
            return tokenClient.checkKeys(app, setup.redirectUri(provider));
        } catch (VendorException unanswered) {
            log.warn("{} gave no answer about workspace {}'s keys ({})", provider, workspaceId, unanswered.getKind());
            throw ApiException.of(ErrorCode.INTEGRATION_PROVIDER_UNREACHABLE);
        }
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
                                               WorkspaceMailIntegration integration, Map<UUID, String> saverNames) {
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
                connections.countThrough(workspaceId, provider, appClientId),
                own ? secretHintOf(integration) : null,
                own ? integration.getSecretSavedAt() : null,
                own && integration.getSecretSavedBy() != null ? saverNames.get(integration.getSecretSavedBy()) : null,
                own ? integration.getKeysCheckedAt() : null,
                own ? integration.getKeysRefusedWith() : null);
    }

    /** A secret saved before its hint was kept is read for it each time, until a new one is saved. */
    private String secretHintOf(WorkspaceMailIntegration integration) {
        if (integration.getSecretHint() != null || !integration.hasSecret() || !cipher.isAvailable()) {
            return integration.getSecretHint();
        }
        try {
            return hintOf(cipher.decrypt(integration.getClientSecretEncrypted(), integration.clientSecretContext()));
        } catch (IllegalStateException unreadable) {
            log.warn("The {} secret of workspace {} could not be read for its hint", integration.getProvider(),
                    integration.getWorkspaceId());
            return null;
        }
    }

    private static String hintOf(String secret) {
        return secret.length() < SHORTEST_HINTED_SECRET ? null : secret.substring(secret.length() - HINT_LENGTH);
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
        // A format character (a zero-width space, a byte-order mark) is invisible and rides along from Teams, Outlook
        // or Confluence as readily as a space does.
        if (secret.codePoints().anyMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point)
                || Character.isISOControl(point) || Character.getType(point) == Character.FORMAT)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret",
                    "A client secret has no spaces or line breaks. Copy its value alone");
        }
        if (provider == IntegrationProvider.MICROSOFT && GUID.matcher(secret).matches()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "clientSecret",
                    "That is the secret's ID. Paste its value instead");
        }
        return secret;
    }

    /**
     * Only an Entra secret expires, within 24 months. The date already on the row stands while the same app keeps its
     * secret, so a lapsed one still re-saves; a new app or a new secret is held to the bounds. Today is UTC with a
     * day's slack either side, since the browser that chose the date reads its own local day.
     */
    private LocalDate expiryOf(IntegrationProvider provider, OwnAppKeys keys, WorkspaceMailIntegration integration,
                               String clientId, String newSecret) {
        LocalDate expiresOn = keys.secretExpiresOn();
        if (provider != IntegrationProvider.MICROSOFT) {
            if (expiresOn != null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "secretExpiresOn",
                        "Only a Microsoft secret expires");
            }
            return null;
        }
        boolean sameSecretKept = newSecret == null && integration.holdsSecretFor(clientId);
        if (expiresOn == null || (sameSecretKept && expiresOn.equals(integration.getSecretExpiresOn()))) {
            return expiresOn;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (expiresOn.isBefore(today.minusDays(1))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "secretExpiresOn", "That date has passed");
        }
        if (expiresOn.isAfter(today.plus(LONGEST_MICROSOFT_SECRET).plusDays(1))) {
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
