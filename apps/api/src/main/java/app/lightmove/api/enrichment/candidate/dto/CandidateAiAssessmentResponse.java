package app.lightmove.api.enrichment.candidate.dto;

import app.lightmove.api.candidate.model.AssessmentSourceLink;
import app.lightmove.api.candidate.model.CandidateAiAssessment;
import app.lightmove.api.candidate.model.CandidateAiEnrichState;
import app.lightmove.api.candidate.model.CompetencyPanelAssessment;
import java.time.Instant;
import java.util.List;

/**
 * A candidate's last AI assessment, as the drawer's staff-only section reads it. The assessment
 * fields are null until a run has succeeded; {@code failedAt} is the last run that produced nothing.
 */
public record CandidateAiAssessmentResponse(String summary, CompetencyPanelAssessment technical,
                                            CompetencyPanelAssessment behavioural,
                                            List<AssessmentSourceLink> sources, String assessedAt,
                                            Instant failedAt) {

    public static CandidateAiAssessmentResponse of(CandidateAiEnrichState state) {
        CandidateAiAssessment assessment = state.assessment();
        if (assessment == null) {
            return new CandidateAiAssessmentResponse(null, null, null, List.of(), null, state.failedAt());
        }
        return new CandidateAiAssessmentResponse(assessment.summary(), assessment.technical(),
                assessment.behavioural(), assessment.sources(), assessment.assessedAt(), state.failedAt());
    }
}
