package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.MandateReason;
import java.util.List;
import java.util.Map;

/**
 * Step two of the brief: why the mandate exists, internal throughout (hence the confidentiality flag here).
 * {@code fieldSources} is this step's slice of {@code Position.fieldSources}.
 */
public record MandateContext(
        MandateReason mandateReason,
        String businessDriver,
        List<PositionPriority> strategicPriorities,
        boolean confidential,
        String internalContext,
        Map<String, FieldSource> fieldSources
) {
}
