package app.lightmove.api.enrichment.candidate.dto;

import app.lightmove.api.candidate.model.AssessmentSourceLink;
import app.lightmove.api.candidate.model.CandidateAiAssessment;
import app.lightmove.api.candidate.model.CompetencyPanelAssessment;
import java.util.List;

/** A candidate's last AI assessment, as the drawer's staff-only section reads it. */
public record CandidateAiAssessmentResponse(String summary, CompetencyPanelAssessment technical,
                                            CompetencyPanelAssessment behavioural,
                                            List<AssessmentSourceLink> sources, String assessedAt) {

    public static CandidateAiAssessmentResponse of(CandidateAiAssessment assessment) {
        return new CandidateAiAssessmentResponse(assessment.summary(), assessment.technical(),
                assessment.behavioural(), assessment.sources(), assessment.assessedAt());
    }
}
