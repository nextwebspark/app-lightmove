package app.lightmove.api.assistant.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One conversation, opened. */
public record AssistantThreadResponse(UUID id, String title, UUID projectId, Instant createdAt,
                                      Instant updatedAt, List<AssistantTurnResponse> turns) {}
