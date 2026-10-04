package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One working day of a consultant's free times; empty when the day is fully booked. */
public record SlotDay(LocalDate date, List<Instant> starts) {

    public SlotDay {
        starts = List.copyOf(starts);
    }
}
