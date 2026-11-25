package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.service.PromptTrial;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The reporting extraction's Try it: an invented position description. Nobody in it exists. */
@Component
@RequiredArgsConstructor
class PositionReportingTrial implements PromptTrial {

    private final PositionReportingProposer proposer;

    @Override
    public String promptId() {
        return "position-extract-reporting";
    }

    @Override
    public String inputName() {
        return "Four direct reports";
    }

    @Override
    public String inputDescription() {
        return "A fictional position description naming who the role reports to, its four direct reports, the "
                + "team's size and a notice period. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return PositionReportingProposer.USER_MESSAGE;
    }

    @Override
    public void run() {
        proposer.read(FictionalPositionDescription.TEXT, FictionalPositionDescription.NOTHING_REDACTED,
                FictionalPositionDescription.TEXT);
    }
}
