package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step two, and what produced it — the mandate-context twin of
 * {@link ProposedPositionDetails}.
 */
public record ProposedMandateContext(ExtractionSource source, List<ExtractedField> fields) {

    public ProposedMandateContext {
        fields = List.copyOf(fields);
    }
}
