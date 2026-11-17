package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.NoteOutcome;
import java.time.Instant;
import java.util.UUID;

/** A logged call as Last call reads it: when, how it went and who logged it, without its words. */
public interface LastCallRow {

    Instant getAt();

    NoteOutcome getOutcome();

    UUID getByUserId();

    /** Null once the author has left the platform. */
    String getByName();
}
