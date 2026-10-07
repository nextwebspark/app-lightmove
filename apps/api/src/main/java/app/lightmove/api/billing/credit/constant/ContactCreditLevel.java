package app.lightmove.api.billing.credit.constant;

/**
 * How far into its contact credits a workspace is this month: the share of the month's plan credits spent, and
 * {@code OUT} once nothing is left to spend at all, bought credits included.
 */
public enum ContactCreditLevel {
    OK,
    EIGHTY,
    NINETY,
    OUT
}
