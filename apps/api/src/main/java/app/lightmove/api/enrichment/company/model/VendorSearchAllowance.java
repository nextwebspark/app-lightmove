package app.lightmove.api.enrichment.company.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many billed name searches a caller may still make. Shared across the threads of one lookup, so
 * a model asking about ten names cannot turn into sixty searches.
 */
public final class VendorSearchAllowance {

    private final int granted;
    private final AtomicInteger remaining;

    public VendorSearchAllowance(int searches) {
        this.granted = searches;
        this.remaining = new AtomicInteger(searches);
    }

    /** Takes one search, or answers false when none is left. */
    public boolean take() {
        return remaining.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0;
    }

    public int used() {
        return granted - remaining.get();
    }
}
