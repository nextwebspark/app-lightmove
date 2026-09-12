package app.lightmove.api.position.model;

import java.util.List;

/**
 * What the model answered when asked to read a position description for step five — raw and
 * unchecked. See {@link ModelDetailsAnswer} for why this is named for where it came from rather than
 * for what it proposes; turning it into {@link ExtractedField} rows happens in
 * {@link app.lightmove.api.position.service.PositionAssessmentProposer}.
 */
public record ModelAssessmentAnswer(
        List<ModelCriterion> criteria,
        List<ModelCompetency> technical,
        List<ModelCompetency> behavioural
) {

    /** One screening criterion, whether it filters (REQUIRED) or breaks ties (PREFERRED), and its snippet. */
    public record ModelCriterion(String text, String mode, String snippet) {}

    /** One weighted competency and the sentence it was read from. Weight travels as a string like every
     * other numeric field the model answers — see {@code PositionAssessmentProposer.weightFieldFrom}. */
    public record ModelCompetency(String name, String description, String weight, String snippet) {}
}
