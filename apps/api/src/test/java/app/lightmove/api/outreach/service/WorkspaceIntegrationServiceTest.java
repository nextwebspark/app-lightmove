package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.RecordingProviderTokenClient;
import app.lightmove.api.TestSecretCiphers;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachGateway;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.config.ProviderAppSettings;
import app.lightmove.api.core.config.ProviderAppsSettings;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationResponse;
import app.lightmove.api.outreach.model.OwnAppKeys;
import app.lightmove.api.outreach.model.ProviderAppKeysReplaced;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final RecordingProviderTokenClient provider = new RecordingProviderTokenClient();
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Instant now = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3600);
    private final WorkspaceIntegrationService service = new WorkspaceIntegrationService(integrations,
            new ProviderAppSetup(properties), cipher, audit, properties, Clock.fixed(now, ZoneOffset.UTC),
            connections, events, new OwnAppKeyCheck(provider, new ProviderAppSetup(properties), rateLimiter),
            new TransactionTemplate(mock(PlatformTransactionManager.class)), users);

    @BeforeEach
    void auditLinesRecordAndChecksAreWithinBudget() {
        when(audit.event(any())).thenReturn(auditLine);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("keys the provider refuses are not saved, and the refusal names the provider's code")
    void refusedKeysSaveNothing() {
        provider.refuseApp(MICROSOFT_APP, "invalid_client AADSTS7000215");

        assertThatThrownBy(() -> save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, SECRET,
                "contoso.onmicrosoft.com", TODAY.plusMonths(6))))
                .isInstanceOfSatisfying(ApiException.class, refusal -> {
                    assertThat(refusal.getCode()).isEqualTo(ErrorCode.INTEGRATION_KEYS_REFUSED);
                    assertThat(refusal.getProperties()).containsEntry("providerError", "AADSTS7000215");
                });
        verify(integrations, never()).save(any());
        verify(connections, never()).withdrawThrough(any(), any(), anyString());
        verify(audit, never()).event(any());
    }

    @Test
    @DisplayName("a provider that gives no answer leaves everything as it was")
    void anUnansweredCheckSavesNothing() {
        provider.answerNothingFor(GOOGLE_APP);

        assertThatThrownBy(() -> save(IntegrationProvider.GOOGLE, new OwnAppKeys(GOOGLE_APP, SECRET, null, null)))
                .isInstanceOfSatisfying(ApiException.class, refusal ->
                        assertThat(refusal.getCode()).isEqualTo(ErrorCode.INTEGRATION_PROVIDER_UNREACHABLE));
        verify(integrations, never()).save(any());
        verify(rateLimiter).tryAcquire("integration-key-check:workspace:" + workspaceId,
                OwnAppKeyCheck.CHECKS_PER_MINUTE, Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("a workspace past its key-check budget asks the provider nothing")
    void keyChecksAreBudgeted() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> save(IntegrationProvider.GOOGLE, new OwnAppKeys(GOOGLE_APP, SECRET, null, null)))
                .isInstanceOfSatisfying(ApiException.class, refusal ->
                        assertThat(refusal.getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThat(provider.checkedKeys()).isEmpty();
    }

    @Test
    @DisplayName("accepted keys are saved with the secret's last four characters, who saved it, and the check")
    void acceptedKeysKeepTheirHintAndCheck() {
        save(IntegrationProvider.GOOGLE, new OwnAppKeys(GOOGLE_APP, SECRET, null, null));

        ArgumentCaptor<WorkspaceMailIntegration> saved = ArgumentCaptor.forClass(WorkspaceMailIntegration.class);
        verify(integrations).save(saved.capture());
        assertThat(saved.getValue().getSecretHint()).isEqualTo("alue");
        assertThat(saved.getValue().getSecretSavedBy()).isEqualTo(adminId);
        assertThat(saved.getValue().getSecretSavedAt()).isEqualTo(now);
        assertThat(saved.getValue().getKeysCheckedAt()).isEqualTo(now);
        assertThat(saved.getValue().getKeysRefusedWith()).isNull();
        assertThat(provider.checkedKeys()).singleElement()
                .satisfies(checked -> assertThat(checked.clientSecret()).isEqualTo(SECRET));
    }

    @Test
    @DisplayName("a save that keeps the secret checks the one held, and leaves its hint and saver as they were")
    void aKeptSecretIsTheOneChecked() {
        held(ownApp(IntegrationProvider.MICROSOFT, MICROSOFT_APP, "contoso.onmicrosoft.com", TODAY.plusMonths(6)));

        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, "", "contoso.example.com",
                TODAY.plusMonths(6)));

        assertThat(provider.checkedKeys()).singleElement().satisfies(checked -> {
            assertThat(checked.clientSecret()).isEqualTo(SECRET);
            assertThat(checked.tenantId()).isEqualTo("contoso.example.com");
        });
        ArgumentCaptor<WorkspaceMailIntegration> saved = ArgumentCaptor.forClass(WorkspaceMailIntegration.class);
        verify(integrations).save(saved.capture());
        assertThat(saved.getValue().getSecretSavedAt()).isNull();
        assertThat(saved.getValue().getKeysCheckedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Verify again keeps the provider's refusal against the secret it was shown, and audits the answer")
    void verifyAgainRecordsTheAnswer() {
        WorkspaceMailIntegration held = ownApp(IntegrationProvider.GOOGLE, GOOGLE_APP, null, null);
        held(held);
        provider.refuseApp(GOOGLE_APP, "invalid_client");

        service.verify(adminId, workspaceId, IntegrationProvider.GOOGLE, null);

        verify(integrations).recordKeysCheck(held.getId(), held.getClientSecretEncrypted(), now, "invalid_client");
        verify(auditLine).detail("keysChecked", "REFUSED");
        verify(auditLine).detailIfPresent("providerError", "invalid_client");
    }

    @Test
    @DisplayName("only an own app with a secret is checked")
    void verifyNeedsAnOwnApp() {
        assertThatThrownBy(() -> service.verify(adminId, workspaceId, IntegrationProvider.GOOGLE, null))
                .isInstanceOfSatisfying(ApiException.class, refusal ->
                        assertThat(refusal.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(provider.checkedKeys()).isEmpty();
    }

    @Test
    @DisplayName("a secret saved before hints were kept shows its last four, read from the ciphertext and kept")
    void aLegacySecretShowsItsHint() {
        WorkspaceMailIntegration legacy = ownApp(IntegrationProvider.GOOGLE, GOOGLE_APP, null, null);
        when(integrations.findByWorkspaceId(workspaceId)).thenReturn(List.of(legacy));

        WorkspaceIntegrationResponse google = service.list(workspaceId).providers().stream()
                .filter(row -> row.provider() == IntegrationProvider.GOOGLE).findFirst().orElseThrow();

        assertThat(google.secretHint()).isEqualTo("alue");
        assertThat(google.secretSavedBy()).isNull();
        verify(integrations).recordSecretHint(legacy.getId(), legacy.getClientSecretEncrypted(), "alue");
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
    @ValueSource(strings = {"GOCSPX-abc\n", " GOCSPX-abc", "GOCSPX-abc ", "GOCSPX abc", "GOCSPX-abc\t", "GOCSPX\u00a0abc",
            "GOCSPX\u200babc", "\ufeffGOCSPX-abc", "GOCSPX-abc\u2060"})
    @DisplayName("a secret carrying a space, a line break or an invisible format character is a paste of more, refused")
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
                new OwnAppKeys(MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com", TODAY.minusDays(2)));
        assertRefusedOn("secretExpiresOn", IntegrationProvider.MICROSOFT,
                new OwnAppKeys(MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com", TODAY.plusMonths(24).plusDays(2)));

        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, SECRET,
                "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b", TODAY.plusMonths(24)));
        verify(integrations).save(any());
    }

    @Test
    @DisplayName("a day of slack either side, for a browser whose day is not UTC's")
    void aDayOfSlackEitherSide() {
        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com",
                TODAY.minusDays(1)));
        save(IntegrationProvider.MICROSOFT, new OwnAppKeys(MICROSOFT_APP, SECRET, "contoso.example.com",
                TODAY.plusMonths(24).plusDays(1)));

        verify(integrations, times(2)).save(any());
    }

    @Test
    @DisplayName("a lapsed date on record is not kept unchecked by a different app, nor by a new secret")
    void aLapsedDateDoesNotPassToAnotherAppOrSecret() {
        held(ownApp(IntegrationProvider.MICROSOFT, MICROSOFT_APP, "contoso.onmicrosoft.com", TODAY.minusDays(30)));

        assertRefusedOn("secretExpiresOn", IntegrationProvider.MICROSOFT, new OwnAppKeys(
                "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e", SECRET, "contoso.onmicrosoft.com", TODAY.minusDays(30)));
        assertRefusedOn("secretExpiresOn", IntegrationProvider.MICROSOFT, new OwnAppKeys(
                MICROSOFT_APP, "Q8~a.new.secret", "contoso.onmicrosoft.com", TODAY.minusDays(30)));
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
