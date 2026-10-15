package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ScorecardReportSection;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The written half of an assessment: five sections, each a consultant's words, the AI's draft, or nothing yet. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScorecardReport(
        String careerSummary,
        String coreValue,
        String concerns,
        String workingStyle,
        String motivation
) {

    public static ScorecardReport empty() {
        return new ScorecardReport(null, null, null, null, null);
    }

    public String textOf(ScorecardReportSection section) {
        return switch (section) {
            case CAREER_SUMMARY -> careerSummary;
            case CORE_VALUE -> coreValue;
            case CONCERNS -> concerns;
            case WORKING_STYLE -> workingStyle;
            case MOTIVATION -> motivation;
        };
    }

    public ScorecardReport withText(ScorecardReportSection section, String text) {
        return new ScorecardReport(
                section == ScorecardReportSection.CAREER_SUMMARY ? text : careerSummary,
                section == ScorecardReportSection.CORE_VALUE ? text : coreValue,
                section == ScorecardReportSection.CONCERNS ? text : concerns,
                section == ScorecardReportSection.WORKING_STYLE ? text : workingStyle,
                section == ScorecardReportSection.MOTIVATION ? text : motivation);
    }
}
