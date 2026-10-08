package app.lightmove.api.billing.seat.service;

import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.seat.model.StaffSeatsChanged;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing's door for {@code workspace}'s staff changes. A staff seat is an active member holding ADMIN or MEMBER;
 * a client representative never takes one. An invoiced workspace is held to the seats agreed with it while
 * enforcement is on; a Stripe-billed one is never refused, its quantity following the staff once the change commits.
 */
@Service
@RequiredArgsConstructor
public class SeatAllowance {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final WorkspaceAccess access;
    private final LightMoveProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** @param joining the staff about to take a seat, invitations still pending included */
    @Transactional
    public void requireRoomFor(UUID workspaceId, int joining) {
        if (!properties.billing().enforce()) {
            return;
        }
        subscriptions.findByWorkspaceId(workspaceId)
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.INVOICED)
                .filter(subscription -> access.activeStaff(workspaceId).size() + joining > subscription.getSeats())
                .ifPresent(full -> {
                    throw ApiException.withProperty(ErrorCode.SEAT_LIMIT_REACHED, "seats", full.getSeats());
                });
    }

    /** Called in the transaction that moved the staff count; Stripe is asked only after it commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStaffSeatsChanged(UUID workspaceId) {
        if (subscriptions.markSeatSyncDue(workspaceId, clock.instant()) > 0) {
            events.publishEvent(new StaffSeatsChanged(workspaceId));
        }
    }
}
