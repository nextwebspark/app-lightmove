package app.lightmove.api.report.dto;

import java.time.Instant;

/** {@code truncated}: a row cap was hit, so the chapters describe a sample; these totals are still whole. */
public record ReportHeadDto(long universeCount, long executivesMapped, boolean truncated, Instant generatedAt) {}
