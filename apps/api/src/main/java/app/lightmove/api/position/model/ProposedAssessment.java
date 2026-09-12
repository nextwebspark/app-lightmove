package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step five, and what produced it — the assessment twin of
 * {@link ProposedCompensation}.
 */
public record ProposedAssessment(ExtractionSource source, List<ExtractedField> fields) {

    public ProposedAssessment {
        fields = List.copyOf(fields);
    }
}
