package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ApproachKind;
import java.time.Instant;
import java.util.UUID;

/** One approach to a person: who on the team, how, when, and on which position (null when none was named). */
public record PersonTouch(UUID personId, UUID projectId, UUID byUserId, ApproachKind kind, Instant at) {}
