package app.lightmove.api.enrichment.company.model;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.modulith.NamedInterface;

/** Billed name searches left, shared across one lookup's threads so ten names cannot become sixty searches. */
@NamedInterface(name = "api", propagate = false)
public final class VendorSearchAllowance {

    private final int granted;
    private final AtomicInteger remaining;

    public VendorSearchAllowance(int searches) {
        this.granted = searches;
        this.remaining = new AtomicInteger(searches);
    }

    public boolean take() {
        return remaining.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0;
    }

    public int used() {
        return granted - remaining.get();
    }
}
