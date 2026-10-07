package app.lightmove.api.billing.credit.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Where a grant's credits came from, which also sets the order a spend drains them in: the month's plan credits
 * first, then anything given, and bought credits last, since they outlive the month.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CreditGrantSource {
    PLAN(0),
    PROMO(1),
    MANUAL(1),
    GOODWILL(1),
    PURCHASED(2);

    private final int drainRank;

    public boolean isGivenByHand() {
        return this == PROMO || this == MANUAL || this == GOODWILL;
    }
}
