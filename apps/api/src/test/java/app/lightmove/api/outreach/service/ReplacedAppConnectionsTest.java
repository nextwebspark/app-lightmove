package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.constant.ZoomConnectionStatus;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** The connections a workspace made through one OAuth app, and their withdrawal when it leaves that app. */
class ReplacedAppConnectionsTest {

    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");

    private final UUID workspaceId = UUID.randomUUID();
    private final MailboxConnectionRepository mailboxes = mock(MailboxConnectionRepository.class);
    private final ZoomConnectionRepository zoomAccounts = mock(ZoomConnectionRepository.class);
    private final MailboxTokens mailboxTokens = mock(MailboxTokens.class);
    private final ZoomTokens zoomTokens = mock(ZoomTokens.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ReplacedAppConnections connections =
            new ReplacedAppConnections(mailboxes, zoomAccounts, mailboxTokens, zoomTokens, events);

    @Test
    @DisplayName("only live Google mailboxes made through that app are withdrawn, in one statement, their calendars let go")
    @SuppressWarnings("unchecked")
    void withdrawsOnlyWhatTheAppConnected() {
        MailboxConnection throughOldApp = mailbox("google", "old-client");
        throughOldApp.holdRecallCalendar("recall-calendar-1");
        MailboxConnection fromBeforeTheColumn = mailbox("google", null);
        MailboxConnection throughAnotherApp = mailbox("google", "new-client");
        MailboxConnection outlook = mailbox("microsoft", "old-client");
        MailboxConnection alreadyWithdrawn = mailbox("google", "old-client");
        alreadyWithdrawn.markAccessWithdrawn();
        when(mailboxes.findByWorkspaceIdAndGateway(workspaceId, MailboxGatewayKind.DIRECT)).thenReturn(
                List.of(throughOldApp, fromBeforeTheColumn, throughAnotherApp, outlook, alreadyWithdrawn));
        when(mailboxes.withdrawLive(anyCollection(), eq(MailboxStatus.ACTIVE), eq(MailboxStatus.ERROR))).thenReturn(2);

        int withdrawn = connections.withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, "old-client");

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(mailboxes).withdrawLive(ids.capture(), eq(MailboxStatus.ACTIVE), eq(MailboxStatus.ERROR));
        assertThat(ids.getValue()).containsExactlyInAnyOrder(throughOldApp.getId(), fromBeforeTheColumn.getId());
        assertThat(withdrawn).isEqualTo(2);
        assertThat(throughOldApp.getStatus()).as("no loaded row is dirtied, so no version can clash")
                .isEqualTo(MailboxStatus.ACTIVE);
        verify(events).publishEvent(new RecallCalendarReleased("recall-calendar-1"));
        verify(mailboxTokens).forget(throughOldApp.getGrantId());
        verify(mailboxTokens).forget(fromBeforeTheColumn.getGrantId());
    }

    @Test
    @DisplayName("a Zoom account made through the app is withdrawn in one statement and its token forgotten")
    @SuppressWarnings("unchecked")
    void withdrawsZoomAccounts() {
        ZoomConnection throughOldApp = zoom("old-zoom");
        ZoomConnection throughAnotherApp = zoom("new-zoom");
        when(zoomAccounts.findByWorkspaceId(workspaceId)).thenReturn(List.of(throughOldApp, throughAnotherApp));
        when(zoomAccounts.withdrawLive(anyCollection(), eq(ZoomConnectionStatus.ACTIVE), eq(ZoomConnectionStatus.ERROR)))
                .thenReturn(1);

        assertThat(connections.withdrawThrough(workspaceId, IntegrationProvider.ZOOM, "old-zoom")).isEqualTo(1);

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(zoomAccounts).withdrawLive(ids.capture(), eq(ZoomConnectionStatus.ACTIVE), eq(ZoomConnectionStatus.ERROR));
        assertThat(ids.getValue()).containsExactly(throughOldApp.getId());
        verify(zoomTokens).forget(throughOldApp.getId());
    }

    @Test
    @DisplayName("the count is one query, a mailbox's provider matched in lower case")
    void countsByQuery() {
        when(mailboxes.countLiveThrough(workspaceId, MailboxGatewayKind.DIRECT, MailboxStatus.ACTIVE, "microsoft",
                "client-1")).thenReturn(6L);
        when(zoomAccounts.countLiveThrough(workspaceId, ZoomConnectionStatus.ACTIVE, "zoom-1")).thenReturn(3L);

        assertThat(connections.countThrough(workspaceId, IntegrationProvider.MICROSOFT, "client-1")).isEqualTo(6);
        assertThat(connections.countThrough(workspaceId, IntegrationProvider.ZOOM, "zoom-1")).isEqualTo(3);
    }

    @Test
    @DisplayName("with no app to leave, nothing is read or withdrawn")
    void noAppNoWithdrawal() {
        assertThat(connections.withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, null)).isZero();
        assertThat(connections.countThrough(workspaceId, IntegrationProvider.ZOOM, null)).isZero();
        verify(mailboxes, never()).findByWorkspaceIdAndGateway(any(), any());
        verify(zoomAccounts, never()).findByWorkspaceId(any());
    }

    @Test
    @DisplayName("nothing made through the app means no update at all")
    void nothingToWithdraw() {
        when(mailboxes.findByWorkspaceIdAndGateway(workspaceId, MailboxGatewayKind.DIRECT))
                .thenReturn(List.of(mailbox("google", "new-client")));

        assertThat(connections.withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, "old-client")).isZero();
        verify(mailboxes, never()).withdrawLive(anyCollection(), any(), any());
    }

    private MailboxConnection mailbox(String provider, String appClientId) {
        GrantedMailbox granted = new GrantedMailbox(MailboxGrants.mintDirect(provider), provider + "@firm.example",
                provider, "refresh", false, appClientId);
        MailboxConnection connection = MailboxConnection.connected(workspaceId, UUID.randomUUID(), granted, "sealed",
                50, ZoneId.of("Asia/Dubai"), NOW);
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        return connection;
    }

    private ZoomConnection zoom(String appClientId) {
        ZoomConnection account = ZoomConnection.connected(workspaceId, UUID.randomUUID(), "zoom-user", "sealed",
                appClientId, NOW);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
