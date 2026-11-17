package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.LastCallResponse;
import java.time.Instant;

/**
 * The details pane's Key dates; each null until it happened. Last call is the person's newest logged call on any
 * position, which the drawer's Contact section reads too.
 */
public record ConversationKeyDatesResponse(Instant addedAt, Instant firstContactedAt, Instant repliedAt,
                                           Instant lastActivityAt, LastCallResponse lastCall) {}
