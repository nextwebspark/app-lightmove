package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A consultant whose mailbox an email to one executive on a position went from, or waits to go from. */
public interface PersonMessageSender {

    UUID getPersonId();

    UUID getSenderUserId();
}
