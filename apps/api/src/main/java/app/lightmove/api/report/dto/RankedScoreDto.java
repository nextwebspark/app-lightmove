package app.lightmove.api.report.dto;

/**
 * One competency's score on an assessment.
 *
 * @param score       0–10, or null where nobody has scored it: it earns nothing and is shown as unscored
 * @param hasEvidence the assessment states evidence for it, read in full from the assessment itself
 */
public record RankedScoreDto(String key, Integer score, boolean hasEvidence) {}
