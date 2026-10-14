package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import java.time.Instant;
import java.util.UUID;

/** A note something points back at, named by its kind and when it was written — never by what it says. */
public record LinkedNote(UUID id, PersonNoteKind kind, Instant writtenAt) {}
