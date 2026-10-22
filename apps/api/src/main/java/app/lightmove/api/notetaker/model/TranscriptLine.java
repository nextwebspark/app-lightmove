package app.lightmove.api.notetaker.model;

import java.time.Instant;

/**
 * One line of a call's transcript: when it was said, by whom, and what. {@code named}: the speaker is a caption's
 * name; otherwise it is the transcriber's own label for a voice no caption matched.
 */
public record TranscriptLine(Instant at, String speaker, String text, boolean named) {}
