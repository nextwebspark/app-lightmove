package app.lightmove.api.candidate.model;

/** The written half of an assessment: five sections, each the consultant's own words or nothing yet. */
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
}
