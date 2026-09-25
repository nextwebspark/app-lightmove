package app.lightmove.api.candidate.constant;

/** What an executive's long-term incentive is paid in. {@link #NONE} is a recorded "no LTIP", never "not established". */
public enum LongTermIncentiveType {

    OPTIONS("options"),

    RSUS("rsus"),

    CASH("cash"),

    NONE("none");

    private final String wireToken;

    LongTermIncentiveType(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    public static LongTermIncentiveType fromValue(String value) {
        for (LongTermIncentiveType type : values()) {
            if (type.wireToken.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
