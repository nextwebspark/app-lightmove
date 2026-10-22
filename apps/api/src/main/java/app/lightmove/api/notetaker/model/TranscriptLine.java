package app.lightmove.api.notetaker.model;

import java.time.Instant;

/** One line of a call's transcript: when it was said, by whom (a caption's name where one matched), and what. */
public record TranscriptLine(Instant at, String speaker, String text) {}
