package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.HiringUrgency;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.StrategicPriority;
import java.util.Set;

/**
 * Step two of the brief: why the mandate exists. Internal throughout — none of it is written for a
 * candidate to read, which is why the confidentiality flag lives here beside the context it governs
 * rather than off on its own.
 */
public record MandateContext(
        MandateReason mandateReason,
        String businessDriver,
        Set<StrategicPriority> strategicPriorities,
        HiringUrgency hiringUrgency,
        boolean confidential,
        String internalContext
) {
}
