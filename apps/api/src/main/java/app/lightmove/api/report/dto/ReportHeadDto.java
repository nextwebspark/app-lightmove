package app.lightmove.api.report.dto;

import java.time.Instant;

/**
 * The figures the report opens with. {@code truncated} says the read hit a row cap
 * ({@code lightmove.report.*}), so the chapters below describe a sample of the mandate rather than
 * all of it — the totals here are still the whole.
 */
public record ReportHeadDto(long universeCount, long executivesMapped, boolean truncated, Instant generatedAt) {}
