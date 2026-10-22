package app.lightmove.api.notetaker.model;

import java.time.Instant;

/** One speaker's turn as the platform's captions showed it, named by the platform; the bot's caption pieces. */
public record CaptionedLine(long id, String speaker, String text, Instant startedAt, Instant updatedAt) {}
