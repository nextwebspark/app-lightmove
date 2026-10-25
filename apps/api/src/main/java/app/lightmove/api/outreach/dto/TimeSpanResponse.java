package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.OpenInterval;
import java.time.Instant;

/** A stretch of time on a consultant's calendar view. */
public record TimeSpanResponse(Instant start, Instant end) {

    public static TimeSpanResponse of(BusyInterval busy) {
        return new TimeSpanResponse(busy.start(), busy.end());
    }

    public static TimeSpanResponse of(OpenInterval open) {
        return new TimeSpanResponse(open.start(), open.end());
    }
}
