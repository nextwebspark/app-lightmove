package app.lightmove.api.billing.plan.model;

/** An active member whose roles grant {@code BILLING_MANAGE}: who billing's emails go to. */
public record BillingManager(String email, String fullName) {
}
