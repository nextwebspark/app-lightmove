package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantStep;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.ProposalOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantTurnResponse(UUID id, UUID threadId, String question, String answer,
                                    List<AssistantStep> steps, AssistantProposal proposal,
                                    ProposalOutcome proposalAccepted, Instant createdAt) {

    public static AssistantTurnResponse of(AssistantTurn turn) {
        return new AssistantTurnResponse(turn.getId(), turn.getThreadId(), turn.getQuestion(),
                turn.getAnswer(), turn.getSteps(), turn.getProposal(), turn.getProposalAccepted(),
                turn.getCreatedAt());
    }
}
