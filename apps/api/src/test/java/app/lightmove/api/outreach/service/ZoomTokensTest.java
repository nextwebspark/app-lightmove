package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.TestSecretCiphers;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A Zoom access token is refreshed only through the app the account was connected through. */
class ZoomTokensTest {

    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");
    private static final ProviderCredentials OWN_ZOOM = new ProviderCredentials(IntegrationProvider.ZOOM,
            CredentialMode.OWN, "meridian-zoom", "zoom-secret", null);

    private final ZoomConnectionRepository connections = mock(ZoomConnectionRepository.class);
    private final ProviderCredentialsResolver resolver = mock(ProviderCredentialsResolver.class);
    private final ProviderTokenClient tokenClient = mock(ProviderTokenClient.class);
    private final SecretCipher cipher = TestSecretCiphers.dev();

    private ZoomConnection connection;
    private ZoomTokens tokens;

    @BeforeEach
    void connectZoom() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        connection = ZoomConnection.connected(workspaceId, userId, "zoom-user-1",
                cipher.encrypt("zoom-refresh-1", ZoomConnection.refreshTokenContext(workspaceId, userId)),
                "meridian-zoom", NOW);
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        when(connections.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(resolver.resolve(workspaceId, IntegrationProvider.ZOOM)).thenReturn(Optional.of(OWN_ZOOM));
        tokens = new ZoomTokens(connections, resolver, cipher, tokenClient,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), mock(OwnAppKeyHealth.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("an account connected through the workspace's app refreshes")
    void theSameAppRefreshes() {
        when(tokenClient.refresh(OWN_ZOOM, "zoom-refresh-1"))
                .thenReturn(new ProviderTokenGrant("zoom-access-1", Duration.ofHours(1), null));

        assertThat(tokens.accessToken(connection)).isEqualTo("zoom-access-1");
        assertThat(connection.canUse()).isTrue();
    }

    @Test
    @DisplayName("an account connected through another app is asked to reconnect, its token never offered")
    void anotherAppIsRefused() {
        ReflectionTestUtils.setField(connection, "appClientId", "the-app-the-workspace-left");

        assertThatThrownBy(() -> tokens.accessToken(connection))
                .isInstanceOfSatisfying(ApiException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.ZOOM_RECONNECT_NEEDED));
        assertThat(connection.canUse()).isFalse();
        verify(tokenClient, never()).refresh(any(), any());
    }

    @Test
    @DisplayName("an account from before its app was recorded learns it from the first refresh")
    void anUnknownAppIsRecorded() {
        ReflectionTestUtils.setField(connection, "appClientId", null);
        when(tokenClient.refresh(OWN_ZOOM, "zoom-refresh-1"))
                .thenReturn(new ProviderTokenGrant("zoom-access-1", Duration.ofHours(1), null));

        tokens.accessToken(connection);

        assertThat(connection.getAppClientId()).isEqualTo("meridian-zoom");
    }
}
