package app.lightmove.api.customcolumn.constant;

/**
 * What a custom column holds, and therefore how a value entered into it is validated and how the grid
 * aligns it. Four primitives and no option list: a select column needs an options table, an editor for
 * it, and a rule for rows holding an option somebody deleted.
 *
 * <p>The value is always stored as the string it was entered as; the type decides whether that string
 * is <i>accepted</i>, not how it is kept, so correcting a column's type after an import does not
 * silently discard the values already in it.
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
