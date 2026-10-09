package app.lightmove.api.billing.credit.constant;

/**
 * How far into its contact credits a workspace is this month: the share of the month's plan credits spent, and
 * {@code OUT} once nothing is left to spend at all, bought credits included.
 */
public enum ContactCreditLevel {
    OK(0),
    EIGHTY(80),
    NINETY(90),
    OUT(100);

    private final int usedPercent;

    ContactCreditLevel(int usedPercent) {
        this.usedPercent = usedPercent;
    }

    /** The share of the month's plan credits spent that reaches this level. */
    public int usedPercent() {
        return usedPercent;
    }
}
