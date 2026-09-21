package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.position.constant.FieldSource;
import java.util.List;
import java.util.Map;

/**
 * Step three of the brief: the shape of the org around the seat.
 *
 * <p>"Reports to" is the parent of the node flagged as the mandate's seat and "direct reports" are its
 * children, so both are readings of one structure rather than a second copy that can disagree. The
 * target start date lives on the project (V8), not here.
 *
 * <p>{@code teamSize} is free text rather than a count: what a consultant writes is "38 across the
 * finance function", and V9's integer kept the 38 and discarded the meaning.
 *
 * <p>{@code fieldSources} is this step's own slice — teamSize, noticeValue, noticeUnit — of
 * {@code Position.fieldSources}. Each org seat carries its own {@code source} instead; the chart is
 * not part of this map.
 */
public record ReportingStructure(
        List<PositionOrgNode> orgChart,
        String teamSize,
        Integer noticeValue,
        NoticeUnit noticeUnit,
        Map<String, FieldSource> fieldSources
) {
}
