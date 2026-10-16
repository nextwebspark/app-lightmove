package app.uncava.api.candidate.dto;

import app.uncava.api.candidate.constant.ScorecardReportSection;
import app.uncava.api.candidate.constant.ScorecardStatus;
import app.uncava.api.candidate.model.CandidateScorecard;
import app.uncava.api.candidate.model.ScorecardReport;
import app.uncava.api.candidate.model.ScorecardTally;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A consultant's assessment of one executive, read against the brief as it stands. Staff-only. {@code version}
 * is null until the first save, and goes back with the next one.
 *
 * @param reportAiDrafted the report sections still holding the AI's draft untouched
 * @param aiDraftedAt     when the AI last drafted it, null when it never has
 */
public record CandidateScorecardResponse(
        String status,
        Long version,
        double overall,
        int scoredCount,
        int criteriaCount,
        List<ScorecardPanelResponse> panels,
        List<ScorecardCriterionResponse> retired,
        ScorecardReportDto report,
        List<String> reportAiDrafted,
        Instant aiDraftedAt,
        Instant updatedAt,
        String updatedByName,
        Instant reviewedAt,
        String reviewedByName
) {

    public static CandidateScorecardResponse of(CandidateScorecard scorecard, ScorecardTally tally,
                                                String updatedByName, String reviewedByName) {
        ScorecardStatus status = scorecard == null ? ScorecardStatus.DRAFT : scorecard.getStatus();
        ScorecardReport report = scorecard == null ? ScorecardReport.empty() : scorecard.getReport();
        Set<String> aiDrafted = scorecard == null ? Set.of() : scorecard.getAiDrafted();
        return new CandidateScorecardResponse(
                status.value(),
                scorecard == null ? null : scorecard.getVersion(),
                tally.overall(),
                tally.scoredCount(),
                tally.criteriaCount(),
                tally.panels().stream().map(panel -> ScorecardPanelResponse.of(panel, aiDrafted)).toList(),
                tally.retired().stream().map(score -> ScorecardCriterionResponse.retired(score, aiDrafted)).toList(),
                ScorecardReportDto.of(report),
                Arrays.stream(ScorecardReportSection.values())
                        .filter(section -> aiDrafted.contains(CandidateScorecard.draftKeyOf(section)))
                        .map(ScorecardReportSection::value)
                        .toList(),
                scorecard == null ? null : scorecard.getAiDraftedAt(),
                scorecard == null ? null : scorecard.getUpdatedAt(),
                updatedByName,
                scorecard == null ? null : scorecard.getReviewedAt(),
                reviewedByName);
    }
}
