package app.lightmove.api.outreach.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Book a call's grid: the consultant's free times over a run of working days, read in their own zone, the
 * first and last days it may page to, where the page before this one starts (null on the first), and whether
 * their own Zoom account can put a Zoom link on the invite.
 */
public record MeetingSlotsResponse(String address, String timeZone, String provider, int minutes,
                                   LocalDate earliestDate, LocalDate latestDate, LocalDate previousFrom,
                                   List<SlotDayResponse> days, boolean zoomOffered) {}
