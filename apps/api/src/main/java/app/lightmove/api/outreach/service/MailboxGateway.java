package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The mail service that holds consultants' mailboxes and sends as them. One implementation per
 * service, routed by {@link RoutingMailboxGateway}; nothing outside it knows which service answered.
 *
 * <p>{@link #send} is never retried by an implementation: a request that timed out may still have
 * been delivered, and a second copy of an approach to an executive is worse than a failure.
 */
public interface MailboxGateway {

    /** False where no mail service is configured; the screens then offer nothing. */
    boolean isOffered();

    /** The mailbox hosts a consultant may connect, in the service's own names. */
    List<String> providers();

    /** {@link #isOffered()} for {@code workspaceId}, whose OAuth apps may differ from every other workspace's. */
    default boolean isOfferedTo(UUID workspaceId) {
        return isOffered();
    }

    /** {@link #providers()} a consultant of {@code workspaceId} can actually connect. */
    default List<String> providersFor(UUID workspaceId) {
        return providers();
    }

    URI authorizationUri(String provider, String loginHint, String state, URI redirectUri);

    /** Redeems the one-time code the consent screen sent back. Never retried: a code is single-use. */
    GrantedMailbox redeem(String code, URI redirectUri);

    /**
     * The consent screen for a consultant of {@code workspaceId}. A gateway whose OAuth app is chosen per
     * workspace (Settings → Integrations) overrides this; Nylas holds one app for everyone.
     */
    default URI authorizationUri(UUID workspaceId, String provider, String loginHint, String state, URI redirectUri) {
        return authorizationUri(provider, loginHint, state, redirectUri);
    }

    /** {@link #redeem(String, URI)} for a consultant of {@code workspaceId}, at the provider the sign-in began at. */
    default GrantedMailbox redeem(UUID workspaceId, String provider, String code, URI redirectUri) {
        return redeem(code, redirectUri);
    }

    /** True where {@code workspaceId}'s next connection at {@code provider} hands back a refresh token to seal. */
    default boolean holdsRefreshTokens(UUID workspaceId, String provider) {
        return false;
    }

    /** A set {@link OutgoingEmail#replyToMessageId()} sends the email as a reply in that message's thread. */
    SentEmail send(String grantId, OutgoingEmail email);

    /** Withdraws the service's access to the mailbox. */
    void revoke(String grantId);

    /**
     * {@link #revoke(String)} with the grant's refresh token, read before the row let it go, for a provider that
     * revokes by the token itself; null for a grant that holds none.
     */
    default void revoke(String grantId, String refreshToken) {
        revoke(grantId);
    }

    /**
     * Reads one webhook delivery: what it says about which mailbox, or nothing for an event outreach
     * does not listen to. A delivery whose signature does not verify is refused with
     * {@code MAILBOX_WEBHOOK_REJECTED} — the endpoint is public, and the signature is its only credential.
     */
    List<MailboxEvent> readWebhook(String signature, byte[] body);

    /**
     * Who wrote into a thread of the mailbox since {@code since}, the mailbox itself included: the poll
     * that finds a reply when a webhook never arrived. Addresses only, never content.
     */
    List<String> senderAddressesInThread(String grantId, String threadId, Instant since);

    /** The timed events on the mailbox's own calendar between {@code from} and {@code to}. */
    List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to);

    /** When the calendar of {@code address}, the mailbox's own, is taken between {@code from} and {@code to}. */
    List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to);

    /**
     * Puts a call on the mailbox's calendar and invites the executive to it. Never retried, as {@link #send}
     * is not: a request that timed out may still have sent the invite.
     */
    CalendarEvent createEvent(String grantId, NewCalendarEvent event);

    /** False where the service's plan carries no booking pages; {@code {{bookingLink}}} is then not offered. */
    boolean isBookingPageOffered();

    /** Creates a public booking page on the mailbox's calendar and answers its id, which the page is opened by. */
    String createBookingPage(String grantId, BookingPageSpec page);
}
