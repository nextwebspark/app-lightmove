package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.List;

/** The Outreach page: its counts, the next email due, and everyone a sequence has reached for, newest first. */
public record OutreachOverviewResponse(OutreachCountsResponse counts, Instant nextSendAt,
                                       List<OutreachRunResponse> people) {}
