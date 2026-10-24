package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** One working day's hours as Settings sends them; a day not sent is not worked. */
public record WorkingDayRequest(
        @NotNull(message = "Choose a day") DayOfWeek day,
        @NotNull(message = "Choose when the day starts") LocalTime start,
        @NotNull(message = "Choose when the day ends") LocalTime end) {}
