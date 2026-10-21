package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;

/** One executive's outbound emails on a position: when the first and the latest went. */
public interface PersonSentTally {

    UUID getPersonId();

    Instant getFirstSentAt();

    Instant getLastSentAt();
}
