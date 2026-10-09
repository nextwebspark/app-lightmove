package app.lightmove.api.billing.payment.model;

/** A Stripe subscription's seat quantity before and after a sync; equal where it already held the count. */
public record SeatQuantityChange(long previous, long current) {

    public boolean added() {
        return current > previous;
    }

    public boolean removed() {
        return current < previous;
    }
}
