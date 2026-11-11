package app.lightmove.api.retention.dto;

import java.time.Instant;
import java.util.List;

/** The people past the period, oldest touch first. Empty, with no cutoff, when the workspace never deletes. */
public record RetentionDueResponse(
        Integer months,
        Instant cutoff,
        long total,
        List<RetentionDuePersonResponse> people
) {}
