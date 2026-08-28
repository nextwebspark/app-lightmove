package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.MandateReason;
import java.util.List;

/**
 * Step two of the brief: why the mandate exists. Internal throughout — none of it is written for a
 * candidate to read, which is why the confidentiality flag lives here beside the context it governs
 * rather than off on its own.
 */
public record MandateContext(
        MandateReason mandateReason,
        String businessDriver,
        List<String> strategicPriorities,
        boolean confidential,
        String internalContext
) {
}
