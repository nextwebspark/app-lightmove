package app.uncava.api.outreach.service;

import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.outreach.model.BookingPageSpec;
import app.uncava.api.outreach.model.BusyInterval;
import app.uncava.api.outreach.model.CalendarEvent;
import app.uncava.api.outreach.model.GrantedMailbox;
import app.uncava.api.outreach.model.MailboxEvent;
import app.uncava.api.outreach.model.NewCalendarEvent;
import app.uncava.api.outreach.model.OutgoingEmail;
import app.uncava.api.outreach.model.ReleasedGrant;
import app.uncava.api.outreach.model.SentEmail;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/** A deployment without a mail service: outreach is not offered, and anything that reaches here is refused. */
public class UnconfiguredMailboxGateway implements MailboxGateway {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public List<String> providers() {
        return List.of();
    }

    @Override
    public URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        throw unavailable();
    }

    @Override
    public GrantedMailbox redeem(String code, URI redirectUri) {
        throw unavailable();
    }

    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        throw unavailable();
    }

    @Override
    public void revoke(ReleasedGrant released) {
        throw unavailable();
    }

    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
    }

    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        throw unavailable();
    }

    @Override
    public List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to) {
        throw unavailable();
    }

    @Override
    public List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to) {
        throw unavailable();
    }

    @Override
    public CalendarEvent createEvent(String grantId, NewCalendarEvent event) {
        throw unavailable();
    }

    @Override
    public boolean isBookingPageOffered() {
        return false;
    }

    @Override
    public String createBookingPage(String grantId, BookingPageSpec page) {
        throw unavailable();
    }

    private static ApiException unavailable() {
        return ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
    }
}
