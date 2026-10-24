package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.WeeklyHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** One working day's hours; a day not listed is not worked. */
public record WorkingDayResponse(DayOfWeek day, LocalTime start, LocalTime end) {

    /** In week order, Monday first, as {@link DayOfWeek} counts it; null for no week at all. */
    public static List<WorkingDayResponse> listOf(WeeklyHours week) {
        if (week == null) {
            return null;
        }
        return week.asMap().entrySet().stream()
                .map(entry -> new WorkingDayResponse(entry.getKey(), entry.getValue().start(), entry.getValue().end()))
                .toList();
    }
}
