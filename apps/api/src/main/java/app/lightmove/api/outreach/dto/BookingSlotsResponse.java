package app.lightmove.api.outreach.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A direct booking page's free times over a run of working days, in the consultant's zone, the first and last
 * days it may page to, and where the page before this one starts (null on the first).
 */
public record BookingSlotsResponse(String timeZone, int minutes, LocalDate earliestDate, LocalDate latestDate,
                                   LocalDate previousFrom, List<SlotDayResponse> days) {}
