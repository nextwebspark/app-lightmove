package app.lightmove.api.notetaker.dto;

import java.time.Instant;

/** A line of the live transcript; {@code team}: the voice is the consultant's or a colleague's. */
public record LiveLineResponse(long seq, Instant at, String speaker, String text, boolean team) {}
