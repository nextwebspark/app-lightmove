package app.lightmove.api.billing.credit.constant;

import app.lightmove.api.core.config.CreditPriceSettings;

/** A paid action, priced in contact credits by {@code lightmove.billing.prices.*}. */
public enum CreditAction {
    EMAIL_FOUND,
    PHONE_FOUND;

    public long priceIn(CreditPriceSettings prices) {
        return switch (this) {
            case EMAIL_FOUND -> prices.emailFound();
            case PHONE_FOUND -> prices.phoneFound();
        };
    }
}
