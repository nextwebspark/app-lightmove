package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.model.AssistantTurn;
import java.time.Instant;
import java.util.UUID;

/** One exchange as the panel renders it. Token counts stay server-side; nobody reads them here. */
public record AssistantTurnResponse(UUID id, UUID threadId, AssistantTurnStatus status, String question,
                                    String answer, String errorCode, Instant createdAt, Instant finishedAt) {

    public static AssistantTurnResponse of(AssistantTurn turn) {
        return new AssistantTurnResponse(turn.getId(), turn.getThreadId(), turn.getStatus(),
                turn.getQuestion(), turn.getAnswer(), turn.getErrorCode(),
                turn.getCreatedAt(), turn.getFinishedAt());
    }
}
