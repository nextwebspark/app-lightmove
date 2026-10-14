package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.constant.ScorecardStatus;
import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.candidate.model.ScorecardReport;
import app.lightmove.api.candidate.model.ScorecardTally;
import java.time.Instant;
import java.util.List;

/**
 * A consultant's assessment of one executive, read against the brief as it stands. Staff-only. {@code version}
 * is null until the first save, and goes back with the next one.
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
        Instant updatedAt,
        String updatedByName,
        Instant reviewedAt,
        String reviewedByName
) {

    public static CandidateScorecardResponse of(CandidateScorecard scorecard, ScorecardTally tally,
                                                String updatedByName, String reviewedByName) {
        ScorecardStatus status = scorecard == null ? ScorecardStatus.DRAFT : scorecard.getStatus();
        ScorecardReport report = scorecard == null ? ScorecardReport.empty() : scorecard.getReport();
        return new CandidateScorecardResponse(
                status.value(),
                scorecard == null ? null : scorecard.getVersion(),
                tally.overall(),
                tally.scoredCount(),
                tally.criteriaCount(),
                tally.panels().stream().map(ScorecardPanelResponse::of).toList(),
                tally.retired().stream().map(ScorecardCriterionResponse::retired).toList(),
                ScorecardReportDto.of(report),
                scorecard == null ? null : scorecard.getUpdatedAt(),
                updatedByName,
                scorecard == null ? null : scorecard.getReviewedAt(),
                reviewedByName);
    }
}
