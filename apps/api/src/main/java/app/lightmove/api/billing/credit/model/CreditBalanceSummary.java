package app.lightmove.api.billing.credit.model;

import org.springframework.modulith.NamedInterface;

/** A workspace's credits: free to spend, and reserved by holds not yet settled. */
@NamedInterface(name = "api", propagate = false)
public record CreditBalanceSummary(long available, long held) {
}
