package app.lightmove.api.common.constant;

/**
 * What the target bonus figure is read against — the unit that makes the number mean something.
 * {@code FIXED_AMOUNT} is the one basis where the figure is money in the package's currency rather
 * than a proportion of something else.
 */
public enum BonusBasis {
    PERCENT_OF_BASE,
    PERCENT_OF_TOTAL_FIXED,
    MONTHS_OF_BASE,
    FIXED_AMOUNT
}
