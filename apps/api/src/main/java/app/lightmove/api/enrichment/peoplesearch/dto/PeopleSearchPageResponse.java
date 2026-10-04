package app.lightmove.api.enrichment.peoplesearch.dto;

import java.util.List;

/** One page of the stored people filter; {@code billed} is the search credits this page spent, zero when cached. */
public record PeopleSearchPageResponse(List<PersonResultDto> people, int page, int pageSize, long total,
                                       int billed, int cached) {}
