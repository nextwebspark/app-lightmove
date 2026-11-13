package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** The day a colleague told the person themselves; today or earlier. */
public record MarkNotifiedRequest(@NotNull LocalDate notifiedOn) {}
