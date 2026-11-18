package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/** When something last happened about one person, without what it was. */
public interface PersonLatestAtRow {

    UUID getPersonId();

    Instant getAt();
}
