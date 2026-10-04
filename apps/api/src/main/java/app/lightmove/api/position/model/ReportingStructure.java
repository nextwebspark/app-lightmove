package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.position.constant.FieldSource;
import java.util.List;
import java.util.Map;

/**
 * Step three of the brief: the org around the mandate's seat, whose parent and children are "reports
 * to" and "direct reports". {@code teamSize} is free text ("38 across the finance function"), not a count.
 */
public record ReportingStructure(
        List<PositionOrgNode> orgChart,
        String teamSize,
        Integer noticeValue,
        NoticeUnit noticeUnit,
        Map<String, FieldSource> fieldSources
) {
}
