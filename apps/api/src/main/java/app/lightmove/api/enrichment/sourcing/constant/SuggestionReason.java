package app.lightmove.api.enrichment.sourcing.constant;

/**
 * Why a person ranks where they do, or why they do not fit: our own codes, each with at most one value from our
 * search or a country code ({@code TITLE_WORD:CFO}), which the panel turns into chips. Never a person's own words.
 */
public enum SuggestionReason {
    /** A word of the role's own title, or its abbreviation, is in the current title. */
    ROLE_WORD,
    /** A word the search added is in the current title. */
    SEARCH_WORD,
    /** A free check confirmed the current title holds the search's words; the title itself was not read. */
    TITLE_CONFIRMED,
    /** The current title's level, as a {@link TitleLevel}. */
    LEVEL,
    /** Lives in the role's own country. */
    LIVES_IN_ROLE_COUNTRY,
    /** Lives in one of the searched countries other than the role's. */
    LIVES_IN_REGION,
    /** Lives outside the searched countries. */
    LIVES_ELSEWHERE,
    FOUND_BY_BOTH,
    /** In the current role under a year. */
    NEW_IN_ROLE,
    /** Doesn't fit: the title is below the lowest level searched. */
    BELOW_LEVEL,
    /** Doesn't fit: the title names none of the search's words. */
    NO_FUNCTION_WORD,
    /** Doesn't fit: the title holds a leave-out word. */
    LEAVE_OUT,
    /** Doesn't fit: the role is the top seat of a business, and the title is another seat. */
    NOT_THE_TOP_SEAT;

    public String code() {
        return name();
    }

    public String with(String value) {
        return value == null || value.isBlank() ? name() : name() + ":" + value;
    }
}
