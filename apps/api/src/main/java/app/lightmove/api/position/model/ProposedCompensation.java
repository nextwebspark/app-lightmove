package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step four, and what produced it — the compensation twin of
 * {@link ProposedPositionDetails}.
 */
public record ProposedCompensation(ExtractionSource source, List<ExtractedField> fields) {

    public ProposedCompensation {
        fields = List.copyOf(fields);
    }
}
