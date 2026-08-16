package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.CriterionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One selection criterion as the snapshot PUT writes it. */
public record CriterionRequest(
        @NotBlank(message = "Enter the criterion")
        @Size(max = 300, message = "That criterion is too long")
        String text,

        @NotNull(message = "Choose Required or Preferred")
        CriterionMode mode,

        boolean fromBrief
) {}
