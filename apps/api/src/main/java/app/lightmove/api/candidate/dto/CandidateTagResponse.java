package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** One of the workspace's tags, and how many of its people hold it. */
public record CandidateTagResponse(
        UUID id,
        String label,
        /** A {@code CandidateTagColour} wire token. */
        String colour,
        boolean retired,
        long holders
) {}
