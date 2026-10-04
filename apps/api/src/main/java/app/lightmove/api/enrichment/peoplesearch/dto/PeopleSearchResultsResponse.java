package app.lightmove.api.enrichment.peoplesearch.dto;

import java.util.List;

/** The stored filter's pages already bought, from the first on, read back free; empty when none are. */
public record PeopleSearchResultsResponse(List<PeopleSearchPageResponse> pages) {}
