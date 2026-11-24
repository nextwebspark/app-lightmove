package app.lightmove.api.position.model;

import app.lightmove.api.core.llm.model.PromptVersionRef;
import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step two, and what produced it — the mandate-context twin of
 * {@link ProposedPositionDetails}.
 */
public record ProposedMandateContext(ExtractionSource source, List<ExtractedField> fields, PromptVersionRef answeredBy) {

    public ProposedMandateContext {
        fields = List.copyOf(fields);
    }

    public ProposedMandateContext(ExtractionSource source, List<ExtractedField> fields) {
        this(source, fields, null);
    }

    /** {@code answeredBy} null: the heuristic answered, or the bundled prompt, which has no version. */
    public ProposedMandateContext answeredBy(PromptVersionRef version) {
        return new ProposedMandateContext(source, fields, version);
    }
}
