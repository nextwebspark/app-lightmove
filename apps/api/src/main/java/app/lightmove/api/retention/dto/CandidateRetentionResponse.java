package app.lightmove.api.retention.dto;

import java.util.List;

/** Settings → Data & retention's period: the workspace's, or the deployment's where it has not chosen. */
public record CandidateRetentionResponse(
        /** Months a candidate nobody works with is kept before they are due; null is never. */
        Integer months,
        /** False while the workspace keeps the deployment's default. */
        boolean chosen,
        /** The deployment's default, null for never. */
        Integer defaultMonths,
        List<Integer> options
) {}
