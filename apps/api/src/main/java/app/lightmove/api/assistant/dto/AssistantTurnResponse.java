package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.model.AssistantTurn;
import java.time.Instant;
import java.util.UUID;

/**
 * One exchange as the panel renders it. Token counts stay server-side; nobody reads them here.
 *
 * <p>{@code proposal} rides along because a refresh reads the thread, not the stream. The events are
 * the turn's live channel and a finished turn's are reachable only by reopening one — so a card the
 * panel must draw after a reload has to arrive here or not at all.
 */
public record AssistantTurnResponse(UUID id, UUID threadId, AssistantTurnStatus status, String question,
                                    String answer, String errorCode, AssistantProposalDto proposal,
                                    Instant createdAt, Instant finishedAt) {

    public static AssistantTurnResponse of(AssistantTurn turn) {
        return of(turn, null);
    }

    public static AssistantTurnResponse of(AssistantTurn turn, AssistantProposalDto proposal) {
        return new AssistantTurnResponse(turn.getId(), turn.getThreadId(), turn.getStatus(),
                turn.getQuestion(), turn.getAnswer(), turn.getErrorCode(), proposal,
                turn.getCreatedAt(), turn.getFinishedAt());
    }
}
