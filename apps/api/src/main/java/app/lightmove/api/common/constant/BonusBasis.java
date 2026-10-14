package app.lightmove.api.common.constant;

import java.math.BigDecimal;

/**
 * What the target bonus figure is read against — the unit that makes the number mean something.
 * {@code FIXED_AMOUNT} is the one basis where the figure is money in the package's currency rather
 * than a proportion of something else.
 */
public enum BonusBasis {
    PERCENT_OF_BASE(new BigDecimal("100")),
    PERCENT_OF_TOTAL_FIXED(new BigDecimal("100")),
    MONTHS_OF_BASE(new BigDecimal("24")),
    FIXED_AMOUNT(null);

    /** The largest figure the basis reads as true; none for money. */
    private final BigDecimal ceiling;

    BonusBasis(BigDecimal ceiling) {
        this.ceiling = ceiling;
    }

    public boolean admits(BigDecimal figure) {
        return figure == null || ceiling == null || figure.compareTo(ceiling) <= 0;
    }

    /** What a figure past {@link #admits} is told, a fixed sentence. */
    public String ceilingMessage() {
        return this == MONTHS_OF_BASE
                ? "A bonus is at most 24 months of base"
                : "A bonus is at most 100% of the figure it is read against";
    }
}
