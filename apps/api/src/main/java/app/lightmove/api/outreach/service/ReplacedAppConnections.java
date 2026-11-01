package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.ZoomConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.ZoomConnectionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The connections a workspace made through one OAuth app at a provider. A refresh token belongs to the client it was
 * issued to, so when the workspace moves to another app those connections cannot be refreshed through the new one —
 * and the provider may answer that as a refused <em>app</em>, which leaves a mailbox waiting without ever asking its
 * consultant to reconnect. They are withdrawn in the change's own transaction instead, as a refused refresh token is.
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
        return provider == IntegrationProvider.ZOOM
                ? zoomThrough(workspaceId, clientId).size()
                : mailboxesThrough(workspaceId, provider, clientId).size();
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
            List<ZoomConnection> accounts = zoomThrough(workspaceId, clientId);
            accounts.forEach(account -> {
                account.markAccessWithdrawn();
                zoomTokens.forget(account.getId());
            });
            return accounts.size();
        }
        List<MailboxConnection> connections = mailboxesThrough(workspaceId, provider, clientId);
        connections.forEach(connection -> {
            connection.markAccessWithdrawn();
            mailboxTokens.forget(connection.getGrantId());
            String released = connection.releaseRecallCalendar();
            if (released != null) {
                events.publishEvent(new RecallCalendarReleased(released));
            }
        });
        return connections.size();
    }

    private List<MailboxConnection> mailboxesThrough(UUID workspaceId, IntegrationProvider provider, String clientId) {
        return mailboxes.findByWorkspaceIdAndGateway(workspaceId, MailboxGatewayKind.DIRECT).stream()
                .filter(connection -> connection.isLiveThrough(provider, clientId))
                .toList();
    }

    private List<ZoomConnection> zoomThrough(UUID workspaceId, String clientId) {
        return zoomAccounts.findByWorkspaceId(workspaceId).stream()
                .filter(account -> account.isLiveThrough(clientId))
                .toList();
    }
}
