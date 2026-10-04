package app.lightmove.api.common.industry.model;

/**
 * One industry in every stored form, from one lookup so a row never disagrees with itself. On an
 * Apollo company {@code v2Label} is V1 renamed, not finer data. All but {@code label} are null when
 * unresolved.
 */
public record ResolvedIndustry(String label, Integer linkedInCode, String v2Label, String sectorGroup) {
}
