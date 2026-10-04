package app.lightmove.api.candidate.constant;

/**
 * What asked for a candidate's AI enrichment: a capture's research landing, the drawer's button, or a
 * Find executives run filing the person.
 */
public enum AiEnrichTrigger {
    CAPTURE,
    BUTTON,

    /** Spends no per-user budget: a run files a batch at once, and its company cap is the brake. */
    SOURCING
}
