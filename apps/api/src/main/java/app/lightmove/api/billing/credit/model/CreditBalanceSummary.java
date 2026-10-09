package app.lightmove.api.billing.credit.model;

/** A workspace's credits: free to spend, and reserved by holds not yet settled. */
public record CreditBalanceSummary(long available, long held) {
}
