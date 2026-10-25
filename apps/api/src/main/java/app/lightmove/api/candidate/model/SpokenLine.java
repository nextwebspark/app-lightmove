package app.lightmove.api.candidate.model;

import java.time.Instant;

/**
 * One line of a recorded call as the AI reads it: when it was said, by whom, and what. {@code team}: the speaker's
 * name is a staff member's of the workspace, so the line is never read as the executive's.
 */
public record SpokenLine(Instant at, String speaker, String text, boolean team) {}
