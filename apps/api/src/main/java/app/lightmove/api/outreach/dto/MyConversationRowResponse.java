package app.lightmove.api.outreach.dto;

import java.util.UUID;

/** One row of the caller's queue on Home: a position's conversation row, and the position it belongs to. */
public record MyConversationRowResponse(UUID projectId, String positionTitle, ConversationRowResponse row) {}
