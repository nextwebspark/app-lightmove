package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.NoteOutcome;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import java.time.Instant;
import org.springframework.modulith.NamedInterface;

/** One of a person's notes as the AI reads it: what kind, when, about which position, what it says and how it went. */
@NamedInterface(name = "api", propagate = false)
public record NoteOnFile(PersonNoteKind kind, Instant writtenAt, String aboutPosition, String body,
                         NoteOutcome outcome) {}
