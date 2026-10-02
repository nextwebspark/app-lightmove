package app.lightmove.api.outreach.model;

/** Something the mail service told us about a connected mailbox, in no service's own words. */
public sealed interface MailboxEvent permits InboundMessage, DeliveryFailure, MailboxAccessWithdrawn,
        CalendarEventChanged, CalendarEventRemoved, BookingMade {

    String grantId();
}
