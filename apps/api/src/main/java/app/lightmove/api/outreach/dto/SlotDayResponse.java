package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One working day of free times; no starts means the day is fully booked. */
public record SlotDayResponse(LocalDate date, List<Instant> starts) {}
