package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.TestSecretCiphers;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachGateway;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.config.ProviderAppSettings;
import app.lightmove.api.core.config.ProviderAppsSettings;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.OwnAppKeys;
import app.lightmove.api.outreach.model.ProviderAppKeysReplaced;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

/**
 * Saving a workspace's own app: the pastes refused before anything is kept, and what a change of app or of keys does
 * to the connections and Recall calendars made through the old ones.
 */
class WorkspaceIntegrationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final String GOOGLE_APP = "acme-app.apps.googleusercontent.com";
    private static final String MICROSOFT_APP = "6a1c3e5f-7b9d-4f2a-8c4e-0d2b4f6a8c0e";
    private static final String SECRET = "GOCSPX-a~very.secret_value";

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final WorkspaceMailIntegrationRepository integrations = mock(WorkspaceMailIntegrationRepository.class);
    private final SecretCipher cipher = TestSecretCiphers.dev();
    private final AuditService audit = mock(AuditService.class);
    private final AuditService.Builder auditLine = mock(AuditService.Builder.class, RETURNS_SELF);
    private final ReplacedAppConnections connections = mock(ReplacedAppConnections.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final LightMoveProperties properties = properties();
    private final WorkspaceIntegrationService service = new WorkspaceIntegrationService(integrations,
            new ProviderAppSetup(properties), cipher, audit, properties,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3600), ZoneOffset.UTC),
            connections, events);

    @BeforeEach
    void auditLinesRecord() {
        when(audit.event(any())).thenReturn(auditLine);
    }

    @Test
    @DisplayName("moving from the shared app to the workspace's own withdraws what was connected through the shared one")
    void sharedToOwnWithdrawsTheSharedConnections() {
        when(connections.withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, "uncava-google-client"))
                .thenReturn(2);

        save(IntegrationProvider.GOOGLE, new OwnAppKeys(GOOGLE_APP, SECRET, null, null));

        verify(connections).withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, "uncava-google-client");
        verify(auditLine).detailIfPresent("connectionsWithdrawn", 2);
        verify(events, never()).publishEvent(any(ProviderAppKeysReplaced.class));
    }

    @Test
    @DisplayName("a new secret for the same app keeps every connection and hands Recall the new keys")
    void newSecretForTheSameAppReachesRecall() {
        held(ownApp(IntegrationProvider.GOOGLE, GOOGLE_APP, null, null));

        save(IntegrationProvider.GOOGLE, new OwnAppKeys(GOOGLE_APP, "GOCSPX-the-next-secret", null, null));

        verify(connections, never()).withdrawThrough(any(), any(), anyString());
        verify(events).publishEvent(new ProviderAppKeysReplaced(workspaceId, IntegrationProvider.GOOGLE));
    }

    @Test
    @DisplayName("a corrected tenant for the same Microsoft app hands Recall the keys again; a new expiry date alone does not")
    void tenantReachesRecallAndExpiryDoesNot() {
        WorkspaceMailIntegration held = ownApp(IntegrationProvider.MICROSOFT, MICROSOFT_APP, "contoso.onmicrosoft.com",
                TODAY.plusMonths(6));
        held(held);

        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, "", "contoso.onmicrosoft.com",
                TODAY.plusMonths(9)));
        verify(events, never()).publishEvent(any(ProviderAppKeysReplaced.class));

        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, "", "contoso.example.com",
                TODAY.plusMonths(9)));
        verify(events).publishEvent(new ProviderAppKeysReplaced(workspaceId, IntegrationProvider.MICROSOFT));
        verify(connections, never()).withdrawThrough(any(), any(), anyString());
    }

    @Test
    @DisplayName("another app's client id withdraws what the old one connected")
    void anotherClientIdWithdrawsTheOldOnesConnections() {
        held(ownApp(IntegrationProvider.GOOGLE, GOOGLE_APP, null, null));

        save(IntegrationProvider.GOOGLE, new OwnAppKeys("other.apps.googleusercontent.com", SECRET, null, null));

        verify(connections).withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, GOOGLE_APP);
        verify(events, never()).publishEvent(any(ProviderAppKeysReplaced.class));
    }

    @Test
    @DisplayName("returning to the shared app withdraws what the workspace's own app connected")
    void returningToSharedWithdrawsTheOwnConnections() {
        held(ownApp(IntegrationProvider.MICROSOFT, MICROSOFT_APP, "contoso.onmicrosoft.com", null));
        when(connections.withdrawThrough(workspaceId, IntegrationProvider.MICROSOFT, MICROSOFT_APP)).thenReturn(6);

        service.useSharedApp(adminId, workspaceId, IntegrationProvider.MICROSOFT, null);

        verify(connections).withdrawThrough(workspaceId, IntegrationProvider.MICROSOFT, MICROSOFT_APP);
        verify(auditLine).detailIfPresent("connectionsWithdrawn", 6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOCSPX-abc\n", " GOCSPX-abc", "GOCSPX-abc ", "GOCSPX abc", "GOCSPX-abc\t", "GOCSPX abc"})
    @DisplayName("a secret carrying a space, a tab or a line break is a paste of more than the value, and is refused")
    void whitespaceInASecretIsRefused(String pasted) {
        assertRefusedOn("clientSecret", IntegrationProvider.GOOGLE, new OwnAppKeys(GOOGLE_APP, pasted, null, null));
    }

    @Test
    @DisplayName("an Entra secret's ID is refused where its value belongs")
    void microsoftSecretIdIsRefused() {
        assertRefusedOn("clientSecret", IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP,
                "8c1d2e3f-0a1b-4c2d-9e8f-7a6b5c4d3e2f", "contoso.onmicrosoft.com", null));
    }

    @Test
    @DisplayName("each provider's client id is held to the shape it issues them in")
    void clientIdShapes() {
        assertRefusedOn("clientId", IntegrationProvider.GOOGLE, new OwnAppKeys("123456-abc", SECRET, null, null));
        assertRefusedOn("clientId", IntegrationProvider.MICROSOFT,
                new OwnAppKeys("contoso-app", SECRET, "contoso.onmicrosoft.com", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"common", "organizations", "consumers", "..", "contoso", ".contoso.com"})
    @DisplayName("a Microsoft tenant is the directory's id or a domain of it, never a multi-tenant alias")
    void tenantMustBeTheDirectory(String tenant) {
        assertRefusedOn("tenantId", IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, SECRET, tenant, null));
    }

    @Test
    @DisplayName("only a Microsoft secret expires, and within Entra's 24 months")
    void expiryRules() {
        assertRefusedOn("secretExpiresOn", IntegrationProvider.GOOGLE,
                new OwnAppKeys(GOOGLE_APP, SECRET, null, TODAY.plusMonths(6)));
        assertRefusedOn("secretExpiresOn", IntegrationProvider.MICROSOFT,
                new OwnAppKeys(MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com", TODAY.minusDays(1)));
        assertRefusedOn("secretExpiresOn", IntegrationProvider.MICROSOFT,
                new OwnAppKeys(MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com", TODAY.plusMonths(24).plusDays(1)));

        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, SECRET,
                "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b", TODAY.plusMonths(24)));
        verify(integrations).save(any());
    }

    @Test
    @DisplayName("an expiry already on the record stands, so an app whose secret lapsed can still be corrected")
    void lapsedExpiryOnRecordStands() {
        held(ownApp(IntegrationProvider.MICROSOFT, MICROSOFT_APP, "contoso.onmicrosoft.com", TODAY.minusDays(3)));

        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, "", "contoso.example.com",
                TODAY.minusDays(3)));

        verify(integrations).save(any());
    }

    private void save(IntegrationProvider provider, OwnAppKeys keys) {
        service.update(adminId, workspaceId, provider, CredentialMode.OWN, keys, null);
    }

    private void assertRefusedOn(String field, IntegrationProvider provider, OwnAppKeys keys) {
        assertThatThrownBy(() -> save(provider, keys))
                .isInstanceOf(ApiException.class)
                .satisfies(refusal -> assertThat(((ApiException) refusal).getFieldErrors()).containsKey(field));
        verify(integrations, never()).save(any());
        verify(connections, never()).withdrawThrough(any(), any(), anyString());
    }

    private void held(WorkspaceMailIntegration integration) {
        when(integrations.findByWorkspaceIdAndProvider(eq(workspaceId), eq(integration.getProvider())))
                .thenReturn(Optional.of(integration));
    }

    private WorkspaceMailIntegration ownApp(IntegrationProvider provider, String clientId, String tenantId,
                                            LocalDate expiresOn) {
        WorkspaceMailIntegration integration = WorkspaceMailIntegration.sharedApp(workspaceId, provider);
        integration.useOwnApp(clientId, cipher.encrypt(SECRET, integration.clientSecretContext()), tenantId, expiresOn,
                adminId);
        return integration;
    }

    private static LightMoveProperties properties() {
        ProviderAppsSettings apps = new ProviderAppsSettings(
                new ProviderAppSettings("uncava-google-client", "uncava-google-secret", List.of(), "", ""),
                new ProviderAppSettings("uncava-microsoft-client", "uncava-microsoft-secret", List.of(), "", ""),
                new ProviderAppSettings("", "", List.of(), "", ""));
        OutreachSettings outreach = new OutreachSettings(null, apps, Duration.ofMinutes(10), 50, 200,
                LocalTime.of(8, 0), LocalTime.of(18, 0), List.of(), 25, Duration.ofMinutes(10), Duration.ofDays(30),
                OutreachGateway.NYLAS, null);
        WebSettings web = new WebSettings("https://app.example", List.of(), "/auth/callback", 0, true, DataSize.ofMegabytes(2));
        return new LightMoveProperties(null, null, web, null, null, null, null, null, null, null, null, null, null,
                null, null, outreach, null, null, null, null, null, null, null, null, null);
    }
}
