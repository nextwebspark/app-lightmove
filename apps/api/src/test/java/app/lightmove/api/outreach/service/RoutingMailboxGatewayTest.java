package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.OutgoingEmail;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which gateway answers: the one that made a connection, and the configured one for a new connection. */
class RoutingMailboxGatewayTest {

    private static final URI CALLBACK = URI.create("https://beta.uncava.com/api/v1/outreach/mailbox/callback");
    private static final OutgoingEmail EMAIL = new OutgoingEmail("priya@client.example", "Hello", "<p>Hi</p>");

    private final MailboxGateway nylas = mock(MailboxGateway.class);
    private final DirectMailboxGateway google = directAt("google");

    @Test
    @DisplayName("a connection is answered by the gateway that made it, whatever new connections go through")
    void callsFollowTheGrant() {
        RoutingMailboxGateway router = router(false);
        String directGrant = MailboxGrants.mintDirect("google");

        router.send("nylas-grant-uuid", EMAIL);
        router.send(directGrant, EMAIL);
        router.revoke(directGrant);

        verify(nylas).send("nylas-grant-uuid", EMAIL);
        verify(google).send(directGrant, EMAIL);
        verify(google).revoke(directGrant);
        verify(nylas, never()).send(eq(directGrant), any());
    }

    @Test
    @DisplayName("a direct grant whose gateway this deployment lacks is refused, never handed to Nylas")
    void anUnknownDirectGrantIsRefused() {
        RoutingMailboxGateway router = router(true);

        assertThatThrownBy(() -> router.send(MailboxGrants.mintDirect("microsoft"), EMAIL))
                .isInstanceOfSatisfying(ApiException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.MAILBOX_UNAVAILABLE));
        verify(nylas, never()).send(any(), any());
    }

    @Test
    @DisplayName("on nylas every new connection goes to Nylas, even where our own gateway exists")
    void nylasModeConnectsThroughNylas() {
        RoutingMailboxGateway router = router(false);
        UUID workspace = UUID.randomUUID();

        router.authorizationUri(workspace, "google", "yara@firm.example", "state", CALLBACK);
        router.redeem(workspace, "google", "code", CALLBACK);

        verify(nylas).authorizationUri(workspace, "google", "yara@firm.example", "state", CALLBACK);
        verify(nylas).redeem(workspace, "google", "code", CALLBACK);
        verify(google, never()).redeem(any(), any(), any(), any());
        assertThat(router.providers()).containsExactly("google", "microsoft");
    }

    @Test
    @DisplayName("on direct our own gateway connects the providers it covers, and Nylas the rest")
    void directModeConnectsThroughOurOwnWhereItCan() {
        RoutingMailboxGateway router = router(true);
        UUID workspace = UUID.randomUUID();

        router.redeem(workspace, "google", "code-g", CALLBACK);
        router.redeem(workspace, "microsoft", "code-m", CALLBACK);

        verify(google).redeem(workspace, "google", "code-g", CALLBACK);
        verify(nylas).redeem(workspace, "microsoft", "code-m", CALLBACK);
        assertThat(router.providers()).containsExactly("google", "microsoft");
        assertThat(router.isOffered()).isTrue();
        assertThat(router.holdsRefreshTokens(workspace, "google")).isTrue();
        assertThat(router.holdsRefreshTokens(workspace, "microsoft")).isFalse();
    }

    @Test
    @DisplayName("a direct gateway with no app to connect through is passed over for Nylas")
    void anUnofferedDirectGatewayIsPassedOver() {
        RoutingMailboxGateway router = router(true);
        when(google.isOffered()).thenReturn(false);
        when(google.isOfferedTo(any())).thenReturn(false);
        UUID workspace = UUID.randomUUID();

        router.redeem(workspace, "google", "code", CALLBACK);

        verify(nylas).redeem(workspace, "google", "code", CALLBACK);
    }

    @Test
    @DisplayName("a workspace with no app at the provider, shared or its own, still connects through Nylas")
    void aWorkspaceWithoutAnAppConnectsThroughNylas() {
        UUID withApp = UUID.randomUUID();
        UUID withoutApp = UUID.randomUUID();
        when(google.isOfferedTo(withApp)).thenReturn(true);
        when(google.isOfferedTo(withoutApp)).thenReturn(false);
        RoutingMailboxGateway router = router(true);

        router.redeem(withApp, "google", "code-1", CALLBACK);
        router.redeem(withoutApp, "google", "code-2", CALLBACK);

        verify(google).redeem(withApp, "google", "code-1", CALLBACK);
        verify(nylas).redeem(withoutApp, "google", "code-2", CALLBACK);
        assertThat(router.holdsRefreshTokens(withoutApp, "google")).isFalse();
        assertThat(router.providersFor(withApp)).containsExactly("google", "microsoft");
        when(nylas.isOffered()).thenReturn(false);
        assertThat(router.providersFor(withoutApp)).isEmpty();
        assertThat(router.isOfferedTo(withoutApp)).isFalse();
        assertThat(router.isOfferedTo(withApp)).isTrue();
    }

    @Test
    @DisplayName("the Nylas webhook and booking pages stay Nylas's")
    void webhooksAndBookingPagesStayWithNylas() {
        RoutingMailboxGateway router = router(true);
        when(nylas.isBookingPageOffered()).thenReturn(true);

        router.readWebhook("signature", new byte[0]);

        verify(nylas).readWebhook(eq("signature"), any());
        assertThat(router.isBookingPageOffered()).isTrue();
    }

    private RoutingMailboxGateway router(boolean connectDirectly) {
        when(nylas.isOffered()).thenReturn(true);
        when(nylas.providers()).thenReturn(List.of("google", "microsoft"));
        return new RoutingMailboxGateway(nylas, List.of(google), connectDirectly);
    }

    private static DirectMailboxGateway directAt(String provider) {
        DirectMailboxGateway gateway = mock(DirectMailboxGateway.class);
        when(gateway.provider()).thenReturn(provider);
        when(gateway.isOffered()).thenReturn(true);
        when(gateway.isOfferedTo(any())).thenReturn(true);
        return gateway;
    }
}
