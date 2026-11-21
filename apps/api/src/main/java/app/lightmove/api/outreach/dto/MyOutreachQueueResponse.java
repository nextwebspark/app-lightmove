package app.lightmove.api.outreach.dto;

import java.util.List;

/** What waits on the caller across the positions they work, most urgent first. {@code total} counts past the cap. */
public record MyOutreachQueueResponse(List<MyConversationRowResponse> rows, long total, boolean truncated) {}
