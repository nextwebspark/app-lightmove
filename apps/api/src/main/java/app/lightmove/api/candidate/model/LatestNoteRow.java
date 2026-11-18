package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;

/** When a person's newest note a position reads happened, without its words. */
public interface LatestNoteRow {

    UUID getPersonId();

    Instant getAt();
}
