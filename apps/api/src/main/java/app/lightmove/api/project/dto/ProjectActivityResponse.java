package app.lightmove.api.project.dto;

import java.time.Instant;

/**
 * One line of the project drawer's activity feed.
 *
 * <p>The sentence is rendered here rather than shipping the ledger row it came from: an audit event's
 * metadata carries emails, file names and provider ids kept for an auditor, and none of that belongs
 * on a drawer.
 */
public record ProjectActivityResponse(String eventType, String summary, String actorName,
                                      String actorAvatarUrl, Instant occurredAt) {}
