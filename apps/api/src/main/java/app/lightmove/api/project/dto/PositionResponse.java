package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.EmploymentType;
import app.lightmove.api.project.constant.MandateReason;
import app.lightmove.api.project.constant.NoticeUnit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The whole position brief. Writes are snapshot PUTs — the screen always holds the whole document
 * and autosaves whole sections — so the write requests stay lenient (no min≤max cross-checks) and
 * only the lock endpoint validates readiness.
 */
public record PositionResponse(
        MandateReason mandateReason,
        String internalContext,
        String narrative,
        String reportsTo,
        Integer directReports,
        Integer teamSize,
        String location,
        EmploymentType employmentType,
        /** The mandate's one target date — sourced from the project, not a position field. */
        LocalDate startTarget,
        Long salaryMin,
        Long salaryMax,
        String currency,
        Integer noticeValue,
        NoticeUnit noticeUnit,
        Integer bonusTargetPct,
        String ltip,
        List<String> benefits,
        boolean confidential,
        List<CriterionResponse> criteria,
        List<CompetencyDto> technical,
        List<CompetencyDto> behavioural,
        boolean locked,
        Instant lockedAt
) {}
