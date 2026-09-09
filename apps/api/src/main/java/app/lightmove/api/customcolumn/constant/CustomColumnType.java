package app.lightmove.api.customcolumn.constant;

/**
 * What a custom column holds, and therefore how a value entered into it is validated and how the grid
 * aligns it.
 *
 * <p>Four primitives and no option list. A select column needs a second table for its options, an
 * editor for that table, and a rule for what happens to rows holding an option somebody deleted —
 * none of which the import needs, and all of which would be guessed at rather than designed. A column
 * of fixed choices is free text until somebody asks for more.
 *
 * <p>The value itself is always stored as the string it was entered as; the type decides whether that
 * string is <i>accepted</i>, not how it is stored. That way correcting a column's type after an
 * import does not silently discard the values already in it.
 */
public enum CustomColumnType {

    TEXT("text"),
    NUMBER("number"),
    DATE("date"),
    BOOLEAN("boolean");

    private final String wireToken;

    CustomColumnType(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    public static CustomColumnType fromValue(String value) {
        for (CustomColumnType type : values()) {
            if (type.wireToken.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
