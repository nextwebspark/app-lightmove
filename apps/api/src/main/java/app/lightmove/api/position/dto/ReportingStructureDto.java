package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.NoticeUnit;
import java.time.LocalDate;
import java.util.List;

/** Step three as the brief returns it. */
public record ReportingStructureDto(
        String reportsToName,
        String reportsTo,
        List<DirectReportDto> directReports,
        String teamSize,
        /** The mandate's one target date — sourced from the project, not a position field. */
        LocalDate targetStart,
        Integer noticeValue,
        NoticeUnit noticeUnit
) {}
