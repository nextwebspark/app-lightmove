package app.lightmove.api.enrichment.sourcing.constant;

/** Our judgement of one person a search found, against the role. Stored by name, matching V213's CHECK. */
public enum SuggestionVerdict {
    /** The current title fits the role, or a free check confirmed it holds the search's words. */
    FITS,
    /** No current title on record, and nothing confirmed one. */
    NO_TITLE,
    /** The current title is below the lowest level searched, names another function, or holds a leave-out word. */
    NOT_FIT
}
