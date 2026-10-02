package app.lightmove.api.outreach.dto;

import java.util.List;

/** Book a call's grid: the consultant's free times over the next working days, read in their own zone. */
public record MeetingSlotsResponse(String address, String timeZone, String provider, int minutes,
                                   List<SlotDayResponse> days) {}
