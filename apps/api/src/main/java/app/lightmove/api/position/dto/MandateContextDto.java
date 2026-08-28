package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.HiringUrgency;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.StrategicPriority;
import java.util.Set;

/** Step two as the brief returns it — internal throughout, never shown to a candidate. */
public record MandateContextDto(
        MandateReason mandateReason,
        String businessDriver,
        Set<StrategicPriority> strategicPriorities,
        HiringUrgency hiringUrgency,
        boolean confidential,
        String internalContext
) {}
