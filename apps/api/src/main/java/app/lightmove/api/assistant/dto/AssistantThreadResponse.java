package app.lightmove.api.assistant.dto;

import java.util.List;
import java.util.UUID;

public record AssistantThreadResponse(UUID id, String title, UUID projectId,
                                      List<AssistantTurnResponse> turns) {
}
