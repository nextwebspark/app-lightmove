package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A step as the editor sends it. The first step's delay is ignored and the others' subject is. */
public record SequenceStepRequest(
        @Min(value = 0, message = "Choose how many working days to wait")
        @Max(value = 30, message = "Wait at most 30 working days")
        int delayWorkingDays,
        @Size(max = 200, message = "That subject is too long")
        String subject,
        @NotBlank(message = "Write this email")
        @Size(max = 5000, message = "That email is too long")
        String body) {}
