package app.lightmove.api.billing.overview.dto;

/** A pack of contact credits Checkout sells here, priced before VAT. */
public record CreditPackOffer(String code, long credits, long priceFils) {
}
