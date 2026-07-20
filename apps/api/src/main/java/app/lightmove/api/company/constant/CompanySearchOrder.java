package app.lightmove.api.company.constant;

/**
 * How a zero-query company browse is ordered. REVENUE_DESC surfaces the prominent names a target
 * list seeds from; REVENUE_ASC surfaces the smallest companies with a known figure — and only those,
 * since ascending over null revenues would list nothing but data gaps. Ignored once the user types:
 * a typed query ranks by match, not revenue.
 */
public enum CompanySearchOrder {

    REVENUE_DESC("revenue_desc"),
    REVENUE_ASC("revenue_asc");

    private final String wireToken;

    CompanySearchOrder(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The wire value; the frontend mirror carries the same tokens. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its order, or {@code null} if unknown. */
    public static CompanySearchOrder fromValue(String value) {
        for (CompanySearchOrder order : values()) {
            if (order.wireToken.equals(value)) {
                return order;
            }
        }
        return null;
    }
}
