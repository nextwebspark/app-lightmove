package app.lightmove.api.company.dto;

import java.util.List;

/** The name search's page of matches. */
public record SearchResponse(List<CompanySearchResult> companies) {}
