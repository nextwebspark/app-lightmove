package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.service.PromptTrial;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The mandate context extraction's Try it: an invented position description. Nobody in it exists. */
@Component
@RequiredArgsConstructor
class PositionContextTrial implements PromptTrial {

    private final PositionContextProposer proposer;

    @Override
    public String promptId() {
        return "position-extract-context";
    }

    @Override
    public String inputName() {
        return "Newly created role";
    }

    @Override
    public String inputDescription() {
        return "A fictional position description that says why the role exists and what the board wants from it. "
                + "No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return PositionContextProposer.USER_MESSAGE;
    }

    @Override
    public void run() {
        proposer.read(FictionalPositionDescription.TEXT, FictionalPositionDescription.NOTHING_REDACTED,
                FictionalPositionDescription.TEXT);
    }
}
