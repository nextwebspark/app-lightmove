package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.model.ProposedPositionDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The position details extraction's Try it: an invented position description. Nobody in it exists. */
@Component
@RequiredArgsConstructor
class PositionDetailsTrial implements PromptTrial {

    /** The heuristic reader matches titles against a workspace's templates, so a trial runs without its reading. */
    private static final ProposedPositionDetails NO_HEURISTIC_READING =
            new ProposedPositionDetails(ExtractionSource.DOCUMENT_HEADINGS, List.of());

    private final PositionDetailsProposer proposer;

    @Override
    public String promptId() {
        return "position-extract-details";
    }

    @Override
    public String inputName() {
        return "Operations chief description";
    }

    @Override
    public String inputDescription() {
        return "A fictional position description with a title, department, location, narrative and "
                + "responsibilities. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return PositionDetailsProposer.USER_MESSAGE;
    }

    @Override
    public void run() {
        proposer.read(FictionalPositionDescription.TEXT, FictionalPositionDescription.NOTHING_REDACTED,
                FictionalPositionDescription.TEXT, NO_HEURISTIC_READING);
    }
}
