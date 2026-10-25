package app.lightmove.api.outreach.model;

import java.time.Instant;

/** A stretch of a consultant's working hours, as instants. */
public record OpenInterval(Instant start, Instant end) {

    public boolean covers(Instant from, Instant to) {
        return !from.isBefore(start) && !to.isAfter(end);
    }
}
