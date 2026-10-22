package app.lightmove.api.notetaker.model;

/**
 * One line the transcriber heard in a piece of audio: how far into the piece it began, and the transcriber's own
 * label for the voice ({@code Speaker 1}), which means nothing outside that piece.
 */
public record HeardLine(long offsetMillis, String speakerLabel, String text) {}
