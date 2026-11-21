package app.lightmove.api.outreach.dto;

import java.util.List;

/** What waits on the caller across the positions they work, most urgent first; {@code truncated} when a cap cut any. */
public record MyOutreachQueueResponse(List<MyConversationRowResponse> rows, boolean truncated) {}
