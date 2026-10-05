package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/** The days and hours a sequence's emails may go, in the sender's zone. */
public record SequenceScheduleRequest(
        @NotEmpty(message = "Choose at least one day to send on")
        Set<@NotNull DayOfWeek> days,
        @NotNull(message = "Choose when sending starts")
        LocalTime windowStart,
        @NotNull(message = "Choose when sending stops")
        LocalTime windowEnd) {}
