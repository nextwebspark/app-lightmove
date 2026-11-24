package app.lightmove.api.position.model;

import app.lightmove.api.core.llm.model.PromptVersionRef;
import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step one, and what produced it — the extraction's twin of {@code
 * dataimport}'s {@code ProposedColumnMappings}.
 */
public record ProposedPositionDetails(ExtractionSource source, List<ExtractedField> fields,
                                      PromptVersionRef answeredBy) {

    public ProposedPositionDetails {
        fields = List.copyOf(fields);
    }

    public ProposedPositionDetails(ExtractionSource source, List<ExtractedField> fields) {
        this(source, fields, null);
    }

    public ProposedPositionDetails answeredBy(PromptVersionRef version) {
        return new ProposedPositionDetails(source, fields, version);
    }
}
