package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.TestSecretCiphers;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachGateway;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.config.ProviderAppSettings;
import app.lightmove.api.core.config.ProviderAppsSettings;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which OAuth app a workspace connects a provider through: Uncava's shared one, or its own. */
class ProviderCredentialsResolverTest {

    private final UUID workspaceId = UUID.randomUUID();
    private final WorkspaceMailIntegrationRepository integrations = mock(WorkspaceMailIntegrationRepository.class);
    private final SecretCipher cipher = TestSecretCiphers.dev();
    private final ProviderCredentialsResolver resolver = new ProviderCredentialsResolver(
            integrations, new ProviderAppSetup(propertiesWithSharedAppsAtGoogleAndMicrosoft()), cipher);

    @Test
    @DisplayName("a workspace that never chose is on the shared app")
    void noRowIsShared() {
        when(integrations.findByWorkspaceIdAndProvider(workspaceId, IntegrationProvider.GOOGLE))
                .thenReturn(Optional.empty());

        ProviderCredentials credentials = resolver.resolve(workspaceId, IntegrationProvider.GOOGLE).orElseThrow();

        assertThat(credentials.mode()).isEqualTo(CredentialMode.SHARED);
        assertThat(credentials.clientId()).isEqualTo("uncava-google-client");
        assertThat(credentials.clientSecret()).isEqualTo("uncava-google-secret");
        assertThat(credentials.tenantId()).isNull();
    }

    @Test
    @DisplayName("a workspace that returned to the shared app gets Uncava's, not what it held before")
    void sharedRowIsShared() {
        WorkspaceMailIntegration returned = ownMicrosoftApp();
        returned.useSharedApp(UUID.randomUUID());
        when(integrations.findByWorkspaceIdAndProvider(workspaceId, IntegrationProvider.MICROSOFT))
                .thenReturn(Optional.of(returned));

        ProviderCredentials credentials = resolver.resolve(workspaceId, IntegrationProvider.MICROSOFT).orElseThrow();

        assertThat(credentials.mode()).isEqualTo(CredentialMode.SHARED);
        assertThat(credentials.clientId()).isEqualTo("uncava-microsoft-client");
    }

    @Test
    @DisplayName("a workspace on its own app gets its own keys, the secret decrypted")
    void ownRowIsDecrypted() {
        when(integrations.findByWorkspaceIdAndProvider(workspaceId, IntegrationProvider.MICROSOFT))
                .thenReturn(Optional.of(ownMicrosoftApp()));

        ProviderCredentials credentials = resolver.resolve(workspaceId, IntegrationProvider.MICROSOFT).orElseThrow();

        assertThat(credentials.mode()).isEqualTo(CredentialMode.OWN);
        assertThat(credentials.clientId()).isEqualTo("contoso-app");
        assertThat(credentials.clientSecret()).isEqualTo("contoso-secret");
        assertThat(credentials.tenantId()).isEqualTo("contoso.onmicrosoft.com");
        assertThat(credentials.toString()).doesNotContain("contoso-secret");
    }

    @Test
    @DisplayName("on the shared mode where this deployment has no shared app, there is nothing to connect through")
    void sharedWithoutAnAppIsEmpty() {
        when(integrations.findByWorkspaceIdAndProvider(workspaceId, IntegrationProvider.ZOOM))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve(workspaceId, IntegrationProvider.ZOOM)).isEmpty();
    }

    private WorkspaceMailIntegration ownMicrosoftApp() {
        WorkspaceMailIntegration integration =
                WorkspaceMailIntegration.sharedApp(workspaceId, IntegrationProvider.MICROSOFT);
        integration.useOwnApp("contoso-app", cipher.encrypt("contoso-secret", integration.clientSecretContext()),
                "contoso.onmicrosoft.com", null, UUID.randomUUID());
        return integration;
    }

    private static LightMoveProperties propertiesWithSharedAppsAtGoogleAndMicrosoft() {
        ProviderAppsSettings apps = new ProviderAppsSettings(
                new ProviderAppSettings("uncava-google-client", "uncava-google-secret", List.of(), "", ""),
                new ProviderAppSettings("uncava-microsoft-client", "uncava-microsoft-secret", List.of(), "", ""),
                new ProviderAppSettings("", "", List.of(), "", ""));
        OutreachSettings outreach = new OutreachSettings(null, apps, Duration.ofMinutes(10), 50,
                LocalTime.of(8, 0), LocalTime.of(18, 0), List.of(), 25, Duration.ofMinutes(10), Duration.ofDays(30),
                OutreachGateway.NYLAS);
        WebSettings web = new WebSettings("https://app.example", List.of(), "/auth/callback", 0);
        return new LightMoveProperties(null, null, web, null, null, null, null, null, null, null, null, null, null,
                null, null, outreach, null, null, null, null, null, null, null);
    }
}
