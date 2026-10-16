package app.uncava.api.candidate.dto;

import app.uncava.api.candidate.model.CandidateScorecard;
import app.uncava.api.candidate.model.ScorecardReport;
import jakarta.validation.constraints.Size;

/** The assessment report's five sections, the same shape read and written. */
public record ScorecardReportDto(
        @Size(max = CandidateScorecard.MAX_REPORT_SECTION, message = "Keep each section under 4,000 characters")
        String careerSummary,
        @Size(max = CandidateScorecard.MAX_REPORT_SECTION, message = "Keep each section under 4,000 characters")
        String coreValue,
        @Size(max = CandidateScorecard.MAX_REPORT_SECTION, message = "Keep each section under 4,000 characters")
        String concerns,
        @Size(max = CandidateScorecard.MAX_REPORT_SECTION, message = "Keep each section under 4,000 characters")
        String workingStyle,
        @Size(max = CandidateScorecard.MAX_REPORT_SECTION, message = "Keep each section under 4,000 characters")
        String motivation
) {

    public static ScorecardReportDto of(ScorecardReport report) {
        return new ScorecardReportDto(report.careerSummary(), report.coreValue(), report.concerns(),
                report.workingStyle(), report.motivation());
    }

    public ScorecardReport toReport() {
        return new ScorecardReport(blankToNull(careerSummary), blankToNull(coreValue), blankToNull(concerns),
                blankToNull(workingStyle), blankToNull(motivation));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
