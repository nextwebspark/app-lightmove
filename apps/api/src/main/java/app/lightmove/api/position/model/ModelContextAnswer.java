package app.lightmove.api.position.model;

import java.util.List;

/**
 * What the model answered when asked to read a position description for step two — raw and
 * unchecked. See {@link ModelDetailsAnswer} for why this is named for where it came from rather than
 * for what it proposes; turning it into {@link ExtractedField} rows happens in
 * {@link app.lightmove.api.position.service.PositionContextProposer}.
 */
public record ModelContextAnswer(
        String mandateReason,
        String mandateReasonSnippet,
        String businessDriver,
        String businessDriverSnippet,
        List<ModelStrategicPriority> strategicPriorities
) {

    /** One strategic priority and the sentence it was read from. */
    public record ModelStrategicPriority(String name, String snippet) {}
}
