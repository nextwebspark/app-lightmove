package app.lightmove.api.billing.payment.model;

/**
 * The card a subscription is charged to, as the payment provider names it.
 *
 * @param brand Stripe's display brand, such as {@code visa} or {@code american_express}
 */
public record PaymentCard(String brand, String last4) {
}
