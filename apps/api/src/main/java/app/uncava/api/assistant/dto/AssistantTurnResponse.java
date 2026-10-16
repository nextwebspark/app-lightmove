package app.uncava.api.assistant.dto;

import app.uncava.api.assistant.model.AssistantProposal;
import app.uncava.api.assistant.model.AssistantQuestion;
import app.uncava.api.assistant.model.AssistantStep;
import app.uncava.api.assistant.model.AssistantTurn;
import app.uncava.api.assistant.model.ProposalOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantTurnResponse(UUID id, UUID threadId, String question, String answer,
                                    List<AssistantStep> steps, AssistantProposal proposal,
                                    List<AssistantQuestion> questions, ProposalOutcome proposalAccepted,
                                    Instant createdAt) {

    public static AssistantTurnResponse of(AssistantTurn turn) {
        return new AssistantTurnResponse(turn.getId(), turn.getThreadId(), turn.getQuestion(),
                turn.getAnswer(), turn.getSteps(), turn.getProposal(), turn.getQuestions(), turn.getProposalAccepted(),
                turn.getCreatedAt());
    }
}
