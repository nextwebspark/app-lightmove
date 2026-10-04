package app.lightmove.api.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The staff-only half of chapter one. The table, KPI pace and per-researcher figures count over
 * {@code from}–{@code to}; coverage and company cards are the mandate as it stands.
 */
public record TeamPerformanceDto(
        LocalDate from,
        LocalDate to,
        int days,
        boolean truncated,
        TeamKpisDto kpis,
        List<CoverageShareDto> coverage,
        List<ResearcherDto> researchers,
        List<CompanyCoverageDto> companies,
        int companiesTotal
) {}
