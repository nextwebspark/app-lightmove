package app.uncava.api.position.model;

import app.uncava.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step five, and what produced it — the assessment twin of
 * {@link ProposedPositionDetails}.
 */
public record ProposedAssessment(ExtractionSource source, List<ExtractedField> fields) {

    public ProposedAssessment {
        fields = List.copyOf(fields);
    }
}
