package app.lightmove.api.core.stream;

/** What changed under a mandate. The client treats every kind the same way — refetch — so a new kind
 * costs nothing on the frontend; the enum exists so a publish site cannot invent a misspelt one. */
public enum ProjectStreamKind {
    CANDIDATE_CAPTURED,
    CANDIDATE_ENRICHED,
    COMPANY_CAPTURED,
    COMPANY_ENRICHED;

    /** The wire form the browser sees: {@code candidate-enriched}. */
    public String wire() {
        return name().toLowerCase().replace('_', '-');
    }
}
