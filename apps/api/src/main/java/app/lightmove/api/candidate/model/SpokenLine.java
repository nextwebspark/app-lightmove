package app.lightmove.api.candidate.model;

import java.time.Instant;

/** One line of a recorded call as the AI reads it: when it was said, by whom, and what. */
public record SpokenLine(Instant at, String speaker, String text) {}
