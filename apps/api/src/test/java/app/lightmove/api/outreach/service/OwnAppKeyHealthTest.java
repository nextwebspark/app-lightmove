package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

/** A refresh's answer about a workspace's own app reaches Settings; Uncava's shared app has no card to turn red. */
class OwnAppKeyHealthTest {

    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");
    private static final ProviderCredentials OWN_MICROSOFT = new ProviderCredentials(IntegrationProvider.MICROSOFT,
            CredentialMode.OWN, "6a1c3e5f-7b9d-4f2a-8c4e-0d2b4f6a8c0e", "secret", "contoso.onmicrosoft.com");
    private static final ProviderCredentials SHARED_MICROSOFT = new ProviderCredentials(IntegrationProvider.MICROSOFT,
            CredentialMode.SHARED, "uncava-microsoft-client", "secret", null);

    private final UUID workspaceId = UUID.randomUUID();
    private final WorkspaceMailIntegrationRepository integrations = mock(WorkspaceMailIntegrationRepository.class);
    private final OwnAppKeyHealth health = new OwnAppKeyHealth(integrations, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a refused own app is recorded against the app that was refused, named by Entra's rule")
    void aRefusedOwnAppIsRecorded() {
        health.refusedAtRefresh(workspaceId, OWN_MICROSOFT, new ProviderAppUnavailable("invalid_client AADSTS7000222"));

        verify(integrations).recordAppRefused(workspaceId, IntegrationProvider.MICROSOFT, CredentialMode.OWN,
                OWN_MICROSOFT.clientId(), NOW, "AADSTS7000222");
    }

    @Test
    @DisplayName("the shared app's answers are nobody's card")
    void theSharedAppIsNotRecorded() {
        health.refusedAtRefresh(workspaceId, SHARED_MICROSOFT, new ProviderAppUnavailable("invalid_client"));
        health.answeredAtRefresh(workspaceId, SHARED_MICROSOFT);

        verifyNoInteractions(integrations);
    }

    @Test
    @DisplayName("an answered refresh writes only to put right a refusal on record")
    void anAnsweredRefreshClearsOnlyARefusal() {
        health.answeredAtRefresh(workspaceId, OWN_MICROSOFT);
        verifyNoInteractions(integrations);

        ProviderCredentials refusedBefore = new ProviderCredentials(IntegrationProvider.MICROSOFT, CredentialMode.OWN,
                OWN_MICROSOFT.clientId(), "secret", "contoso.onmicrosoft.com", true);
        health.answeredAtRefresh(workspaceId, refusedBefore);
        verify(integrations).clearAppRefused(workspaceId, IntegrationProvider.MICROSOFT, CredentialMode.OWN,
                OWN_MICROSOFT.clientId(), NOW);
    }

    @Test
    @DisplayName("failing to record never changes what the refresh answers")
    void aFailedRecordIsSwallowed() {
        when(integrations.recordAppRefused(any(), any(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("slow"));

        assertThatCode(() -> health.refusedAtRefresh(workspaceId, OWN_MICROSOFT,
                new ProviderAppUnavailable("invalid_client"))).doesNotThrowAnyException();
    }
}
