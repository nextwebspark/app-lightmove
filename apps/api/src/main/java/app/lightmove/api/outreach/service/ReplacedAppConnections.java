package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.constant.ZoomConnectionStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The connections a workspace made through one OAuth app at a provider. A refresh token belongs to the client it was
 * issued to, so when the workspace moves to another app those connections are withdrawn in the change's own
 * transaction. This is the eager half: {@link MailboxTokens} and {@link ZoomTokens} refuse a token issued to another app at its
 * next refresh too, which catches a connect that commits while the switch is being saved.
 */
@Service
@RequiredArgsConstructor
public class ReplacedAppConnections {

    private final MailboxConnectionRepository mailboxes;
    private final ZoomConnectionRepository zoomAccounts;
    private final MailboxTokens mailboxTokens;
    private final ZoomTokens zoomTokens;
    private final ApplicationEventPublisher events;

    /** Live mailboxes, or Zoom accounts, connected through {@code clientId}; zero when no app resolves. */
    public int countThrough(UUID workspaceId, IntegrationProvider provider, String clientId) {
        if (clientId == null) {
            return 0;
        }
        long count = provider == IntegrationProvider.ZOOM
                ? zoomAccounts.countLiveThrough(workspaceId, ZoomConnectionStatus.ACTIVE, clientId)
                : mailboxes.countLiveThrough(workspaceId, MailboxGatewayKind.DIRECT, MailboxStatus.ACTIVE,
                        provider.name().toLowerCase(Locale.ROOT), clientId);
        return Math.toIntExact(count);
    }

    /**
     * Withdraws every live connection made through {@code clientId}: each reads as "reconnect" from then on, its
     * access token is dropped from memory, and a mailbox's Recall calendar is let go once this commits.
     *
     * @return how many were withdrawn
     */
    public int withdrawThrough(UUID workspaceId, IntegrationProvider provider, String clientId) {
        if (clientId == null) {
            return 0;
        }
        if (provider == IntegrationProvider.ZOOM) {
            List<ZoomConnection> accounts = zoomAccounts.findByWorkspaceId(workspaceId).stream()
                    .filter(account -> account.isLiveThrough(clientId))
                    .toList();
            if (accounts.isEmpty()) {
                return 0;
            }
            int withdrawn = zoomAccounts.withdrawLive(accounts.stream().map(ZoomConnection::getId).toList(),
                    ZoomConnectionStatus.ACTIVE, ZoomConnectionStatus.ERROR);
            accounts.forEach(account -> zoomTokens.forget(account.getId()));
            return withdrawn;
        }
        List<MailboxConnection> connections =
                mailboxes.findByWorkspaceIdAndGateway(workspaceId, MailboxGatewayKind.DIRECT).stream()
                        .filter(connection -> connection.isLiveThrough(provider, clientId))
                        .toList();
        if (connections.isEmpty()) {
            return 0;
        }
        List<String> calendarsToRelease = connections.stream()
                .map(MailboxConnection::getRecallCalendarId)
                .filter(Objects::nonNull)
                .toList();
        int withdrawn = mailboxes.withdrawLive(connections.stream().map(MailboxConnection::getId).toList(),
                MailboxStatus.ACTIVE, MailboxStatus.ERROR);
        connections.forEach(connection -> mailboxTokens.forget(connection.getGrantId()));
        calendarsToRelease.forEach(calendarId -> events.publishEvent(new RecallCalendarReleased(calendarId)));
        return withdrawn;
    }
}
