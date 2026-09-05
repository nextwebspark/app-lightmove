package app.lightmove.api.dataimport.constant;

/** What worked out a sheet's column mapping. The mapping step says which, and they differ in how far
 * they are worth trusting. */
public enum MappingSource {

    /** Every header was a known spelling or an existing column of this project. */
    EXACT_HEADERS("exactHeaders"),

    /**
     * The header matcher had to guess at least one column — the case worth saying out loud, because
     * it is confident about far less and the mapping is worth reading before it is committed.
     */
    HEADER_MATCHER("headerMatcher");

    private final String wireToken;

    MappingSource(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }
}
