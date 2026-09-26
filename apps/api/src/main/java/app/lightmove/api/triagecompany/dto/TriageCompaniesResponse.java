package app.lightmove.api.triagecompany.dto;

import java.util.List;

/** One page of a stage, plus the sub-nav's counts — sent every time so no badge lags. */
public record TriageCompaniesResponse(List<TriageCompanyResponse> companies, long totalCount, int page,
                                int size, TriageCountsDto counts) {}
