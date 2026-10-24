package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A booked call's new time: any start on a five-minute mark, and its length. */
public record RescheduleMeetingRequest(@NotNull Instant startsAt, @NotNull Integer minutes) {}
