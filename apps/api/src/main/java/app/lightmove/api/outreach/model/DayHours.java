package app.lightmove.api.outreach.model;

import java.time.LocalTime;

/** One working day's hours, local to whoever keeps them: open from {@code start}, closed from {@code end}. */
public record DayHours(LocalTime start, LocalTime end) {

    public DayHours {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("A working day needs a start before its end");
        }
    }
}
