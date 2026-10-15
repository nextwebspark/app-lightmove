package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import java.time.Instant;

/** One of a person's notes as the AI reads it: what kind, when, about which position, and what it says. */
public record NoteOnFile(PersonNoteKind kind, Instant writtenAt, String aboutPosition, String body) {}
