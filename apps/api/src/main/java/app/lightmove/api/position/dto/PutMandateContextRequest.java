package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.MandateReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of step two. */
public record PutMandateContextRequest(
        @NotNull(message = "Choose a reason for the mandate")
        MandateReason mandateReason,

        @Size(max = 1000, message = "That is too long — a sentence or two, not an essay")
        String businessDriver,

        @Size(max = 20, message = "That is too many strategic priorities")
        List<@Valid StrategicPriorityDto> strategicPriorities,

        boolean confidential,

        @Size(max = 4000, message = "That context note is too long")
        String internalContext
) {}
