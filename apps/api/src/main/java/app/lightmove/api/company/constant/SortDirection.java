package app.lightmove.api.company.constant;

/**
 * Which way a chosen sort column runs. Kept separate from {@link CompanySortField} so the client asks
 * for a column and a direction independently — the Sourcing table's headers cycle direction without
 * changing which column is active.
 */
public enum SortDirection {

    ASC("asc", "ASC"),
    DESC("desc", "DESC");

    private final String wireToken;
    private final String sqlKeyword;

    SortDirection(String wireToken, String sqlKeyword) {
        this.wireToken = wireToken;
        this.sqlKeyword = sqlKeyword;
    }

    /** The wire value; the frontend mirror carries the same tokens. */
    public String value() {
        return wireToken;
    }

    /** The SQL keyword this direction emits — never the caller's string. */
    public String sqlKeyword() {
        return sqlKeyword;
    }

    /** Resolve a wire value to its direction, or {@code null} if unknown. */
    public static SortDirection fromValue(String value) {
        for (SortDirection direction : values()) {
            if (direction.wireToken.equals(value)) {
                return direction;
            }
        }
        return null;
    }
}
