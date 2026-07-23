package app.lightmove.api.core.llm.model;

import java.util.List;

/**
 * Everything a {@code BriefLlmClient} could find in a position-description document, mapped onto the
 * position brief's fields. Deliberately its own type, not {@code project.dto.PositionDtos}'s
 * {@code PositionDetails} — {@code core} must never depend on a feature, so the mapping into the real
 * Position fields (including enum parsing and the null-means-"not found" merge) happens in
 * {@code project.service.PositionService}, the only place that is allowed to know both shapes.
 *
 * <p>Every field is nullable: the document may simply not mention it, and a client returning "not
 * found" is a normal outcome, not a failure.
 */
public record BriefExtraction(
        /** Free-form text expected to match {@code MandateReason} (e.g. "NEW_ROLE"); unparsed here. */
        String mandateReason,
        String internalContext,
        String narrative,
        String reportsTo,
        Integer directReports,
        Integer teamSize,
        String location,
        /** Free-form text expected to match {@code EmploymentType}; unparsed here. */
        String employmentType,
        Long salaryMin,
        Long salaryMax,
        String currency,
        Integer noticeValue,
        /** Free-form text expected to match {@code NoticeUnit} ("WEEKS"/"MONTHS"); unparsed here. */
        String noticeUnit,
        Integer bonusTargetPct,
        String ltip,
        List<String> benefits,
        List<ExtractedCriterion> criteria,
        List<ExtractedCompetency> technical,
        List<ExtractedCompetency> behavioural
) {

    public static BriefExtraction empty() {
        return new BriefExtraction(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, List.of(), List.of(), List.of(), List.of());
    }

    /** @param mode free-form text expected to match {@code CriterionMode} ("REQUIRED"/"PREFERRED"). */
    public record ExtractedCriterion(String text, String mode) {}

    public record ExtractedCompetency(String name, int weight) {}
}
