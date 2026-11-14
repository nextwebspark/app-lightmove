package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import java.time.Instant;
import java.util.UUID;

/** A logged call, meeting, email, LinkedIn or WhatsApp message, without its words. */
public interface LoggedContactRow {

    UUID getPersonId();

    UUID getProjectId();

    UUID getAuthorUserId();

    PersonNoteKind getKind();

    Instant getAt();
}
