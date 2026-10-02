package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One meeting with an executive, from a teammate's calendar. {@code joinUrl} is only on one still to
 * come; {@code videoProvider} is the calendar's own name for it, which the drawer prints as the link.
 */
public record MeetingResponse(UUID id, String title, Instant startsAt, Instant endsAt, UUID ownerUserId,
                              String ownerName, String joinUrl, String videoProvider, boolean viaLink,
                              boolean bookedInUncava) {}
