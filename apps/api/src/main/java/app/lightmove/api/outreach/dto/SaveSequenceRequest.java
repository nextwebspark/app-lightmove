package app.lightmove.api.outreach.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A sequence as the editor saves it. A null schedule keeps the one held, or the default working week on a new one. */
public record SaveSequenceRequest(
        @NotBlank(message = "Name the sequence")
        @Size(max = 120, message = "That name is too long")
        String name,
        @NotNull
        @Size(min = 1, max = 3, message = "A sequence has one to three emails")
        List<@Valid @NotNull SequenceStepRequest> steps,
        @Valid
        SequenceScheduleRequest schedule) {}
