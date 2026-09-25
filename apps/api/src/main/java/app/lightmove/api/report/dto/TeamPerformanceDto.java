package app.lightmove.api.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Who on the team is mapping the mandate, and how fully — the staff-only half of chapter one.
 * {@code from}–{@code to} is the range the table, the KPIs' pace and each researcher's figures are
 * counted over; coverage and the company cards are the mandate as it stands, whatever the range.
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
