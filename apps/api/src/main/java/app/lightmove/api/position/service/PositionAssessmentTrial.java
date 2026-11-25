package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.positiontemplate.model.PositionTemplateCompetency;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The assessment extraction's Try it: an invented position description and an invented template's names. */
@Component
@RequiredArgsConstructor
class PositionAssessmentTrial implements PromptTrial {

    private static final List<PositionTemplateCompetency> USUAL_COMPETENCIES = List.of(
            new PositionTemplateCompetency(CompetencyPanel.TECHNICAL, "Operations management", null, 40),
            new PositionTemplateCompetency(CompetencyPanel.TECHNICAL, "Safety and compliance", null, 30),
            new PositionTemplateCompetency(CompetencyPanel.TECHNICAL, "Capital planning", null, 30),
            new PositionTemplateCompetency(CompetencyPanel.BEHAVIOURAL, "Leading change", null, 50),
            new PositionTemplateCompetency(CompetencyPanel.BEHAVIOURAL, "Developing leaders", null, 50));

    private final PositionAssessmentProposer proposer;

    @Override
    public String promptId() {
        return "position-extract-assessment";
    }

    @Override
    public String inputName() {
        return "Operations chief description";
    }

    @Override
    public String inputDescription() {
        return "A fictional position description stating its requirements and weighted competencies, read beside "
                + "a fictional template's competency names. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return PositionAssessmentProposer.USER_MESSAGE;
    }

    @Override
    public void run() {
        proposer.read(FictionalPositionDescription.TEXT, PositionAssessmentProposer.vocabularyOf(USUAL_COMPETENCIES),
                FictionalPositionDescription.NOTHING_REDACTED, FictionalPositionDescription.TEXT);
    }
}
