package app.lightmove.api.project.dto;

import java.util.List;

/**
 * One page of Sourcing results: the company universe filtered by the project's saved Strategy scope
 * (sectors + company size).
 */
public record SourcingResponse(List<CompanyResultDto> companies, long totalCount, int page, int size) {}
