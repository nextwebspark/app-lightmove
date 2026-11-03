package app.lightmove.api.enrichment.sourcing.constant;

/** Who last wrote a position's search: the role title's own words, the AI's draft, or a person. Stored by name (V182). */
public enum CriteriaSource {
    DEFAULT,
    AI,
    EDITED
}
