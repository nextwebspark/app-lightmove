package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A booked call's new time: one of the offered starts, and its length. */
public record RescheduleMeetingRequest(@NotNull Instant startsAt, @NotNull Integer minutes) {}
