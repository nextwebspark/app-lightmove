package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A consultant with an own email still waiting to go to one executive on a position. */
public interface PersonScheduledSender {

    UUID getPersonId();

    UUID getSenderUserId();
}
