package app.lightmove.api.notetaker.model;

import java.time.Instant;

/**
 * One line of a call's transcript: when it was said, by whom, and what. {@code named}: the speaker is the name the
 * participant joined with; otherwise it is Recall's own label for a voice it could not name.
 */
public record TranscriptLine(Instant at, String speaker, String text, boolean named) {}
