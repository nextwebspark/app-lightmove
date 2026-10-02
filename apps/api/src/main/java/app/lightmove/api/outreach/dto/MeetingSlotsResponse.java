package app.lightmove.api.outreach.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Book a call's grid: the consultant's free times over a run of working days, read in their own zone, the
 * first and last days it may page to, and where the page before this one starts (null on the first).
 */
public record MeetingSlotsResponse(String address, String timeZone, String provider, int minutes,
                                   LocalDate earliestDate, LocalDate latestDate, LocalDate previousFrom,
                                   List<SlotDayResponse> days) {}
