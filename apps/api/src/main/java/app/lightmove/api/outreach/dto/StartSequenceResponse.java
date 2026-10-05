package app.lightmove.api.outreach.dto;

import java.time.Instant;

/**
 * How many people are now scheduled on the sequence, and when the first and the last of their first emails
 * are due. Nothing has been sent; a {@code NEXT_WINDOW} start, or a full day's cap, can still move them later.
 */
public record StartSequenceResponse(int enrolled, Instant firstSendAt, Instant lastFirstSendAt) {}
