package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.SendingWindow;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** The days, in week order, and the hours a sequence's emails may go. */
public record SequenceScheduleResponse(List<DayOfWeek> days, LocalTime windowStart, LocalTime windowEnd) {

    public static SequenceScheduleResponse of(SendingWindow window) {
        return new SequenceScheduleResponse(window.workingDays().stream().sorted().toList(), window.start(),
                window.end());
    }
}
