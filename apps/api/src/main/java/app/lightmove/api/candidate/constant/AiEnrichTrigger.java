package app.lightmove.api.candidate.constant;

/**
 * What asked for a candidate's automatic AI run: a capture's research landing, a Find executives run filing the
 * person, or a People Search filing. The drawer's two buttons run synchronously and never pass through here.
 */
public enum AiEnrichTrigger {
    CAPTURE,

    /** Spends no per-user budget: a run files a batch at once, and its company cap is the brake. */
    SOURCING,

    /** Spends no per-user budget either: one press of the selection bar files a page of people. */
    PEOPLE_SEARCH
}
