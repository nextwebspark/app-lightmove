package app.lightmove.api.outreach.model;

import tools.jackson.databind.JsonNode;

/**
 * One event as Recall lists it: the provider's own event in {@code raw}, beside Recall's copies of its ids and
 * whether it was deleted.
 */
public record RecallCalendarEvent(String platform, String platformId, String icalUid, JsonNode raw,
                                  boolean deleted) {}
