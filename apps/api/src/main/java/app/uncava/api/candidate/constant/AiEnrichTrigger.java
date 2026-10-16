package app.uncava.api.candidate.constant;

/**
 * What asked for a candidate's automatic background run: a capture's research landing, or a Find executives run
 * filing the person. The drawer's two buttons run synchronously and never pass through here.
 */
public enum AiEnrichTrigger {
    CAPTURE,

    /** Spends no per-user budget: a run files a batch at once, and its company cap is the brake. */
    SOURCING
}
