package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * Book a call's grid: the consultant's free times over the next working days, read in their own zone, and whether
 * their own Zoom account can put a Zoom link on the invite.
 */
public record MeetingSlotsResponse(String address, String timeZone, String provider, int minutes,
                                   List<SlotDayResponse> days, boolean zoomOffered) {}
