package app.uncava.api.billing.credit.model;

import app.uncava.api.billing.credit.constant.CreditGrantSource;

/** What a workspace's grants of one source still hold spendable, unheld. */
public record SourceCredits(CreditGrantSource source, long remaining) {
}
