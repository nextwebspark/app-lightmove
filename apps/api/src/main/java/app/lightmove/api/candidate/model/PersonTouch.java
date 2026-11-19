package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ApproachKind;
import app.lightmove.api.candidate.constant.NoteOutcome;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * One approach to a person: who on the team, how, when, and on which position (null when none was named). A logged
 * call or WhatsApp message carries how it went; anything else carries no outcome.
 */
@NamedInterface(name = "api", propagate = false)
public record PersonTouch(UUID personId, UUID projectId, UUID byUserId, ApproachKind kind, Instant at,
                          NoteOutcome outcome) {}
