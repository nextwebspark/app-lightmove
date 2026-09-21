package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.MandateReason;
import java.util.List;
import java.util.Map;

/**
 * Step two of the brief: why the mandate exists. Internal throughout — none of it is written for a
 * candidate to read, which is why the confidentiality flag lives here beside the context it governs
 * rather than off on its own.
 *
 * <p>{@code fieldSources} is this step's own slice — mandateReason, businessDriver — of
 * {@code Position.fieldSources}.
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
