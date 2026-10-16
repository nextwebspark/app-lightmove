package app.uncava.api.billing.payment.dto;

/** A Stripe page to send the admin to: Checkout or the Customer Portal. */
public record BillingRedirectResponse(String url) {}
