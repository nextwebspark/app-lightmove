package app.lightmove.api.project.model;

import app.lightmove.api.project.constant.EmploymentType;
import app.lightmove.api.project.constant.MandateReason;
import app.lightmove.api.project.constant.NoticeUnit;
import java.util.List;

/**
 * The scalar snapshot of a position brief — everything outside the criteria and competency lists.
 * Built from the update request on the autosave path, and by {@code PositionTemplates} when a new
 * project's brief is seeded. The target date is deliberately absent: the mandate keeps one target
 * date, on the project, and the position write path sets it there.
 */
public record PositionDetails(
        MandateReason mandateReason,
        String internalContext,
        String narrative,
        String reportsTo,
        Integer directReports,
        Integer teamSize,
        String location,
        EmploymentType employmentType,
        Long salaryMin,
        Long salaryMax,
        String currency,
        Integer noticeValue,
        NoticeUnit noticeUnit,
        Integer bonusTargetPct,
        String ltip,
        List<String> benefits,
        boolean confidential
) {
}
