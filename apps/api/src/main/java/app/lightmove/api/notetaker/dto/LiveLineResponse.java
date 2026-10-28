package app.lightmove.api.notetaker.dto;

import java.time.Instant;

/**
 * A line of the live transcript. {@code team}: the consultant's or a colleague's voice; {@code executive}: a voice
 * named as the executive, the only one that wears their photo; {@code you}: the reader's own voice.
 */
public record LiveLineResponse(long seq, Instant at, String speaker, String text, boolean team, boolean executive,
                               boolean you) {}
