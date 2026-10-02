package app.lightmove.api.outreach.model;

import java.time.Instant;

/** A stretch of a consultant's calendar that is taken. */
public record BusyInterval(Instant start, Instant end) {

    public boolean overlaps(Instant from, Instant to) {
        return start.isBefore(to) && end.isAfter(from);
    }
}
