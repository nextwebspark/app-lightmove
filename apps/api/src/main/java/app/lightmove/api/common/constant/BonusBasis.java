package app.lightmove.api.common.constant;

import java.math.BigDecimal;

/**
 * What the target bonus figure is read against — the unit that makes the number mean something.
 * {@code FIXED_AMOUNT} is the one basis where the figure is money in the package's currency rather
 * than a proportion of something else.
 */
public enum BonusBasis {
    PERCENT_OF_BASE(new BigDecimal("100"), "% of base"),
    PERCENT_OF_TOTAL_FIXED(new BigDecimal("100"), "% of total fixed pay"),
    MONTHS_OF_BASE(new BigDecimal("24"), " months of base"),
    FIXED_AMOUNT(null, null);

    /** The largest figure the basis reads as true; none for money. */
    private final BigDecimal ceiling;

    /** What the figure counts, as it follows the number in a sentence. */
    private final String unit;

    BonusBasis(BigDecimal ceiling, String unit) {
        this.ceiling = ceiling;
        this.unit = unit;
    }

    public boolean admits(BigDecimal figure) {
        return figure == null || ceiling == null || figure.compareTo(ceiling) <= 0;
    }

    /** What a figure past {@link #admits} is told: a fixed sentence built from the ceiling itself. */
    public String ceilingMessage() {
        return "A bonus is at most " + ceiling.toPlainString() + unit;
    }
}
