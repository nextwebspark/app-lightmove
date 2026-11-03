package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.constant.CriteriaSource;
import java.time.Instant;

/**
 * The position's saved search, who last wrote it — the role title's own words, the AI or a person — and whether
 * the brief has changed since the AI drafted it, so the panel can offer Reset.
 */
public record SourcingCriteriaResponse(SourcingCriteriaDto criteria, CriteriaSource source, boolean briefChanged,
                                       Instant updatedAt) {}
