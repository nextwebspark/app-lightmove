package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.HiringUrgency;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.StrategicPriority;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Snapshot PUT of step two. */
public record PutMandateContextRequest(
        @NotNull(message = "Choose a reason for the mandate")
        MandateReason mandateReason,

        String businessDriver,

        Set<StrategicPriority> strategicPriorities,

        @NotNull(message = "Choose a hiring urgency")
        HiringUrgency hiringUrgency,

        boolean confidential,
        String internalContext
) {}
