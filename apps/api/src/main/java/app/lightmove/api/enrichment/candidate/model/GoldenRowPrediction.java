package app.lightmove.api.enrichment.candidate.model;

/**
 * What a subject answered for one golden row. {@code nationality} is a group label or "Unknown";
 * {@code confidence} is null for a subject that states none (the baseline); {@code seniority} is null
 * when the subject proposed no level.
 */
public record GoldenRowPrediction(String rowId, String nationality, String confidence, String seniority) {}
