package app.lightmove.api.billing.seat.model;

import java.util.UUID;

/** A Stripe-billed workspace's staff count moved; its subscription's quantity is synced once this commits. */
public record StaffSeatsChanged(UUID workspaceId) {
}
