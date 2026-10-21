package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;

/** When a consultant's own email to one executive is next due. */
public interface PersonNextSend {

    UUID getPersonId();

    Instant getSendAt();
}
