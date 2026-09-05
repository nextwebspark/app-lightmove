package app.lightmove.api.dataimport.constant;

/** What worked out a sheet's column mapping. The mapping step says which, and they differ in how far
 * they are worth trusting. */
public enum MappingSource {

    /** Every header was a known spelling or an existing column of this project. No model call was made. */
    EXACT_HEADERS("exactHeaders"),

    /** The model resolved the columns the header matcher was unsure of. */
    MODEL("model"),

    /**
     * The header matcher answered alone because the model could not be reached — the ordinary case
     * without Application Default Credentials, and the one worth saying out loud: it is confident
     * about far less.
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
