package app.lightmove.api.outreach.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/**
 * One working day's hours, local to whoever keeps them: open from {@code start}, closed from {@code end}. Written as
 * ISO times ("09:00"), which Hibernate's JSON mapper would otherwise store as arrays of numbers.
 */
public record DayHours(@JsonFormat(shape = JsonFormat.Shape.STRING) LocalTime start,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) LocalTime end) {

    public DayHours {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("A working day has to end after it starts");
        }
    }
}
