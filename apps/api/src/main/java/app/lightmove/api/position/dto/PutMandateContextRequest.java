package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.MandateReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of step two. */
public record PutMandateContextRequest(
        @NotNull(message = "Choose a reason for the mandate")
        MandateReason mandateReason,

        String businessDriver,

        @Size(max = 20, message = "That is too many strategic priorities")
        List<@NotBlank(message = "Enter the priority")
             @Size(max = 120, message = "That priority is too long") String> strategicPriorities,

        boolean confidential,
        String internalContext
) {}
