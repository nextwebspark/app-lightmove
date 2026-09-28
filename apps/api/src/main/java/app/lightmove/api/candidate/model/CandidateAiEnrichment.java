package app.lightmove.api.candidate.model;

/**
 * What one AI enrichment run produced. {@code background} and {@code assessment} come from the
 * assessment call and are null together when it failed; {@code nationalityReading} is the classifier's,
 * null when nationality was already recorded or the classifier failed.
 */
public record CandidateAiEnrichment(InferredBackground background, CandidateAiAssessment assessment,
                                    NationalityReading nationalityReading) {

    public boolean isAssessed() {
        return assessment != null;
    }
}
