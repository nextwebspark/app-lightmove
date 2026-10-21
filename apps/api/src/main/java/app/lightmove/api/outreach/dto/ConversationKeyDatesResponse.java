package app.lightmove.api.outreach.dto;

import java.time.Instant;

/** The details pane's Key dates; each null until it happened. */
public record ConversationKeyDatesResponse(Instant addedAt, Instant firstContactedAt, Instant repliedAt,
                                           Instant lastActivityAt) {}
