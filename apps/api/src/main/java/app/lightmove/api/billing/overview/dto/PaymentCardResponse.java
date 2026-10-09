package app.lightmove.api.billing.overview.dto;

/** Both null where the workspace pays by no card, or Stripe could not be asked. */
public record PaymentCardResponse(String brand, String last4) {
}
