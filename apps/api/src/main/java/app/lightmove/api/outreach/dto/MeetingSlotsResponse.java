package app.lightmove.api.outreach.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Book a call's grid: the consultant's free times over a run of working days, read in their own zone, and
 * the first and last days the grid may page to.
 */
public record MeetingSlotsResponse(String address, String timeZone, String provider, int minutes,
                                   LocalDate earliestDate, LocalDate latestDate, List<SlotDayResponse> days) {}
