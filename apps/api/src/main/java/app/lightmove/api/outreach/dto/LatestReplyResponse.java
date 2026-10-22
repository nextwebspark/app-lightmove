package app.lightmove.api.outreach.dto;

import java.time.Instant;

/**
 * The person's newest reply on the position, for the drawer's notice: when, and the start of what they wrote — null
 * where the mailbox does not read replies' words. Staff-only, as every outreach read is.
 */
public record LatestReplyResponse(Instant at, String text) {}
