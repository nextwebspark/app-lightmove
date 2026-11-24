package app.lightmove.api.position.model;

import app.lightmove.api.core.llm.model.PromptVersionRef;
import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step five, and what produced it — the assessment twin of
 * {@link ProposedPositionDetails}.
 */
public record ProposedAssessment(ExtractionSource source, List<ExtractedField> fields,
                                 PromptVersionRef answeredBy) {

    public ProposedAssessment {
        fields = List.copyOf(fields);
    }

    public ProposedAssessment(ExtractionSource source, List<ExtractedField> fields) {
        this(source, fields, null);
    }

    public ProposedAssessment answeredBy(PromptVersionRef version) {
        return new ProposedAssessment(source, fields, version);
    }
}
