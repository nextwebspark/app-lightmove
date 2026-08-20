package app.lightmove.api.triagecompany.dto;

import java.util.List;

/**
 * One page of a mandate's triaged universe, plus the counts behind the status sub-nav.
 *
 * <p>The counts travel with every page because the sub-nav is always visible and a badge that only
 * refreshed when its own tab was opened would be wrong on the tab you were looking at.
 */
public record TriageCompaniesResponse(List<TriageCompanyResponse> companies, long totalCount, int page,
                                int size, TriageCountsDto counts) {}
