package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ExtractionSource;
import java.util.List;

/**
 * A proposed reading of step three, and what produced it — the reporting twin of
 * {@link ProposedAssessment}.
 *
 * <p>Every field here is a title or a scalar, never a chart: see {@code PositionReportingProposer}'s
 * class doc for why this proposer must not answer with an {@code OrgNode} tree of its own.
 */
public record ProposedReportingStructure(ExtractionSource source, List<ExtractedField> fields) {

    public ProposedReportingStructure {
        fields = List.copyOf(fields);
    }
}
