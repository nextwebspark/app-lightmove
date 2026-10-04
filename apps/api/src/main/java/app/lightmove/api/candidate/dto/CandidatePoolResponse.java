package app.lightmove.api.candidate.dto;

import java.util.List;

/** A page of the workspace's people, with the counts the page's chips and header read. */
public record CandidatePoolResponse(
        List<CandidatePoolRowResponse> people,
        /** How many match the whole query, the quick view included. */
        long totalCount,
        CandidatePoolViewCountsResponse viewCounts,
        /** Everyone the workspace holds, whatever the filters. */
        long poolSize,
        /** The countries the pool's people are recorded in, for the Country filter. */
        List<String> countries
) {}
