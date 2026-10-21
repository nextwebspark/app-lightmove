package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.List;

/** One tab of the Outreach page's queue. {@code truncated} says the position held more than one read returns. */
public record ConversationListResponse(ConversationCountsResponse counts, Instant nextScheduledAt,
                                       List<ConversationRowResponse> rows, boolean truncated) {}
