package app.lightmove.api.enrichment.candidate.dto;

import app.lightmove.api.candidate.model.CandidateAiAssessment;
import app.lightmove.api.candidate.model.CandidateAiEnrichState;
import app.lightmove.api.candidate.model.CompetencyPanelAssessment;
import app.lightmove.api.candidate.model.NationalityReading;
import java.time.Instant;

/**
 * A candidate's last AI assessment and nationality reading, as the drawer's staff-only sections read
 * them. The assessment fields are null until a run has succeeded; {@code nationalityReading} is null
 * until the classifier has answered; {@code failedAt} is the last run that produced nothing.
 */
public record CandidateAiAssessmentResponse(String summary, CompetencyPanelAssessment technical,
                                            CompetencyPanelAssessment behavioural, String assessedAt,
                                            NationalityReading nationalityReading, Instant failedAt) {

    public static CandidateAiAssessmentResponse of(CandidateAiEnrichState state) {
        CandidateAiAssessment assessment = state.assessment();
        if (assessment == null) {
            return new CandidateAiAssessmentResponse(null, null, null, null, state.nationalityReading(),
                    state.failedAt());
        }
        return new CandidateAiAssessmentResponse(assessment.summary(), assessment.technical(),
                assessment.behavioural(), assessment.assessedAt(), state.nationalityReading(), state.failedAt());
    }
}
