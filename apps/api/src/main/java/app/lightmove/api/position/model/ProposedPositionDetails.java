package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step one, and what produced it — the extraction's twin of {@code
 * dataimport}'s {@code ProposedColumnMappings}.
 */
public record ProposedPositionDetails(ExtractionSource source, List<ExtractedField> fields) {

    public ProposedPositionDetails {
        fields = List.copyOf(fields);
    }
}
