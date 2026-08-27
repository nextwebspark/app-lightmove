package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.NoticeUnit;
import java.util.List;

/**
 * Step three of the brief: the shape of the org around the seat. The target start date is absent for
 * the same reason the role title is absent from {@link PositionDetails} — the mandate keeps one target
 * date, on the project (V8).
 *
 * <p>{@code teamSize} is free text rather than a count: what a consultant actually writes is "38
 * across the finance function", and V9's integer kept the 38 and discarded the meaning.
 */
public record ReportingStructure(
        String reportsToName,
        String reportsTo,
        List<PositionDirectReport> directReports,
        String teamSize,
        Integer noticeValue,
        NoticeUnit noticeUnit
) {
}
