package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

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
    @DisplayName("only live Google mailboxes made through that app are withdrawn, their calendars let go")
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

        assertThat(connections.countThrough(workspaceId, IntegrationProvider.GOOGLE, "old-client")).isEqualTo(2);
        int withdrawn = connections.withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, "old-client");

        assertThat(withdrawn).isEqualTo(2);
        assertThat(throughOldApp.getStatus()).isEqualTo(MailboxStatus.ERROR);
        assertThat(throughOldApp.getRecallCalendarId()).isNull();
        assertThat(fromBeforeTheColumn.getStatus()).isEqualTo(MailboxStatus.ERROR);
        assertThat(throughAnotherApp.getStatus()).isEqualTo(MailboxStatus.ACTIVE);
        assertThat(outlook.getStatus()).isEqualTo(MailboxStatus.ACTIVE);
        verify(events).publishEvent(new RecallCalendarReleased("recall-calendar-1"));
        verify(mailboxTokens).forget(throughOldApp.getGrantId());
        verify(mailboxTokens).forget(fromBeforeTheColumn.getGrantId());
    }

    @Test
    @DisplayName("a Zoom account made through the app is withdrawn and its token forgotten")
    void withdrawsZoomAccounts() {
        ZoomConnection throughOldApp = ZoomConnection.connected(workspaceId, UUID.randomUUID(), "zoom-user-1",
                "sealed", "old-zoom", NOW);
        ZoomConnection throughAnotherApp = ZoomConnection.connected(workspaceId, UUID.randomUUID(), "zoom-user-2",
                "sealed", "new-zoom", NOW);
        when(zoomAccounts.findByWorkspaceId(workspaceId)).thenReturn(List.of(throughOldApp, throughAnotherApp));

        assertThat(connections.withdrawThrough(workspaceId, IntegrationProvider.ZOOM, "old-zoom")).isEqualTo(1);

        assertThat(throughOldApp.canUse()).isFalse();
        assertThat(throughAnotherApp.canUse()).isTrue();
    }

    @Test
    @DisplayName("with no app to leave, nothing is read or withdrawn")
    void noAppNoWithdrawal() {
        assertThat(connections.withdrawThrough(workspaceId, IntegrationProvider.GOOGLE, null)).isZero();
        assertThat(connections.countThrough(workspaceId, IntegrationProvider.ZOOM, null)).isZero();
        verify(mailboxes, never()).findByWorkspaceIdAndGateway(any(), any());
        verify(zoomAccounts, never()).findByWorkspaceId(any());
    }

    private MailboxConnection mailbox(String provider, String appClientId) {
        GrantedMailbox granted = new GrantedMailbox(MailboxGrants.mintDirect(provider), provider + "@firm.example",
                provider, "refresh", false, appClientId);
        return MailboxConnection.connected(workspaceId, UUID.randomUUID(), granted, "sealed", 50,
                ZoneId.of("Asia/Dubai"), NOW);
    }
}
