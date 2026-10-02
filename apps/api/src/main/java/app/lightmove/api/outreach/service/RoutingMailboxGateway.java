package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The {@link MailboxGateway} everything above the seam talks to. A connection is always answered by the gateway
 * that made it, read off its grant id; a new one is made through {@code lightmove.outreach.gateway} — our own
 * gateway where it covers the provider, Nylas otherwise, so Nylas stays the fallback until it is retired.
 */
public class RoutingMailboxGateway implements MailboxGateway {

    private final MailboxGateway nylas;
    private final Map<String, DirectMailboxGateway> directByProvider;
    private final MailboxGatewayKind connectThrough;

    public RoutingMailboxGateway(MailboxGateway nylas, List<DirectMailboxGateway> direct,
                                 MailboxGatewayKind connectThrough) {
        this.nylas = nylas;
        this.connectThrough = connectThrough;
        Map<String, DirectMailboxGateway> byProvider = new LinkedHashMap<>();
        direct.forEach(gateway -> byProvider.put(gateway.provider(), gateway));
        this.directByProvider = Map.copyOf(byProvider);
    }

    @Override
    public boolean isOffered() {
        return nylas.isOffered() || !connectableDirectly().isEmpty();
    }

    /** Our own gateway's providers first, then whatever Nylas still covers. */
    @Override
    public List<String> providers() {
        Set<String> providers = new LinkedHashSet<>(connectableDirectly());
        if (nylas.isOffered()) {
            providers.addAll(nylas.providers());
        }
        return List.copyOf(providers);
    }

    /** Names no workspace, so only Nylas, whose one app serves everyone, can answer it. */
    @Override
    public URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        return nylas.authorizationUri(provider, loginHint, state, redirectUri);
    }

    @Override
    public GrantedMailbox redeem(String code, URI redirectUri) {
        return nylas.redeem(code, redirectUri);
    }

    @Override
    public URI authorizationUri(UUID workspaceId, String provider, String loginHint, String state, URI redirectUri) {
        return connectingAt(provider).authorizationUri(workspaceId, provider, loginHint, state, redirectUri);
    }

    @Override
    public GrantedMailbox redeem(UUID workspaceId, String provider, String code, URI redirectUri) {
        return connectingAt(provider).redeem(workspaceId, provider, code, redirectUri);
    }

    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        return holding(grantId).send(grantId, email);
    }

    @Override
    public void revoke(String grantId) {
        holding(grantId).revoke(grantId);
    }

    /** The Nylas endpoint's: our own gateway's providers report through their own routes. */
    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        return nylas.readWebhook(signature, body);
    }

    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        return holding(grantId).senderAddressesInThread(grantId, threadId, since);
    }

    @Override
    public List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to) {
        return holding(grantId).calendarEvents(grantId, from, to);
    }

    @Override
    public List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to) {
        return holding(grantId).busyTimes(grantId, address, from, to);
    }

    @Override
    public CalendarEvent createEvent(String grantId, NewCalendarEvent event) {
        return holding(grantId).createEvent(grantId, event);
    }

    /** Booking pages are Nylas Scheduler's; nothing of ours offers them yet. */
    @Override
    public boolean isBookingPageOffered() {
        return nylas.isBookingPageOffered();
    }

    @Override
    public String createBookingPage(String grantId, BookingPageSpec page) {
        return holding(grantId).createBookingPage(grantId, page);
    }

    MailboxGateway connectingAt(String provider) {
        DirectMailboxGateway direct = directByProvider.get(provider);
        if (connectThrough == MailboxGatewayKind.DIRECT && direct != null && direct.isOffered()) {
            return direct;
        }
        return nylas;
    }

    /** A direct grant whose gateway this deployment no longer has is refused, never handed to Nylas. */
    MailboxGateway holding(String grantId) {
        if (MailboxGatewayKind.ofGrant(grantId) == MailboxGatewayKind.NYLAS) {
            return nylas;
        }
        return MailboxGrants.directProviderOf(grantId)
                .map(directByProvider::get)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE));
    }

    private List<String> connectableDirectly() {
        if (connectThrough != MailboxGatewayKind.DIRECT) {
            return List.of();
        }
        return directByProvider.values().stream()
                .filter(MailboxGateway::isOffered)
                .map(DirectMailboxGateway::provider)
                .toList();
    }
}
