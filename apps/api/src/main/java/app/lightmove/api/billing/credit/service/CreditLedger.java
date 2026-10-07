package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.CreditEntryKind;
import app.lightmove.api.billing.credit.constant.CreditHoldStatus;
import app.lightmove.api.billing.credit.model.CreditBalance;
import app.lightmove.api.billing.credit.model.CreditBalanceSummary;
import app.lightmove.api.billing.credit.model.CreditCharge;
import app.lightmove.api.billing.credit.model.CreditEntry;
import app.lightmove.api.billing.credit.model.CreditGrant;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.model.CreditGrantReceipt;
import app.lightmove.api.billing.credit.model.CreditHold;
import app.lightmove.api.billing.credit.model.CreditReceipt;
import app.lightmove.api.billing.credit.repository.CreditBalanceRepository;
import app.lightmove.api.billing.credit.repository.CreditEntryRepository;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.credit.repository.CreditHoldRepository;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.config.BillingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only door to a workspace's contact credits. Every write locks the workspace's balance row first, so writes
 * to one workspace run one at a time; each is a short transaction of its own, and a vendor call belongs between
 * a {@link #hold} and its {@link #capture} or {@link #release}, never inside either.
 */
@Service
@RequiredArgsConstructor
public class CreditLedger {

    private final CreditBalanceRepository balances;
    private final CreditGrantRepository grants;
    private final CreditHoldRepository holds;
    private final CreditEntryRepository entries;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final LightMoveProperties properties;
    private final Clock clock;

    /**
     * Reserves the action's price from the workspace's grants. Refused with {@code INSUFFICIENT_CREDITS} when they
     * cannot cover it and enforcement is on; with it off, the shortfall is recorded as an overdraft at capture.
     */
    @Transactional
    public CreditReceipt hold(CreditCharge charge) {
        CreditBalance balance = lock(charge.workspaceId());
        return holds.findByWorkspaceIdAndIdempotencyKey(charge.workspaceId(), charge.idempotencyKey())
                .map(CreditHold::receipt)
                .orElseGet(() -> open(charge, balance).receipt());
    }

    /** {@link #hold} and {@link #capture} at once, for a spend that has already happened. */
    @Transactional
    public CreditReceipt charge(CreditCharge charge) {
        CreditBalance balance = lock(charge.workspaceId());
        return holds.findByWorkspaceIdAndIdempotencyKey(charge.workspaceId(), charge.idempotencyKey())
                .map(CreditHold::receipt)
                .orElseGet(() -> {
                    CreditHold hold = open(charge, balance);
                    spend(hold, balance);
                    return hold.receipt();
                });
    }

    @Transactional
    public CreditReceipt capture(UUID workspaceId, UUID holdId) {
        CreditBalance balance = lock(workspaceId);
        CreditHold hold = requireHold(workspaceId, holdId);
        if (hold.getStatus() == CreditHoldStatus.OPEN) {
            spend(hold, balance);
        } else if (hold.getStatus() == CreditHoldStatus.RELEASED) {
            throw new IllegalStateException("hold " + holdId + " was released and cannot be captured");
        }
        return hold.receipt();
    }

    @Transactional
    public CreditReceipt release(UUID workspaceId, UUID holdId) {
        CreditBalance balance = lock(workspaceId);
        CreditHold hold = requireHold(workspaceId, holdId);
        if (hold.getStatus() != CreditHoldStatus.RELEASED) {
            hold.settle(CreditHoldStatus.OPEN, CreditHoldStatus.RELEASED);
            returnToGrants(hold, balance, CreditEntryKind.RELEASE);
        }
        return hold.receipt();
    }

    /** Gives a captured spend's credits back to the grants it drained; an overdraft has nothing to give back. */
    @Transactional
    public CreditReceipt refund(UUID workspaceId, UUID holdId) {
        CreditBalance balance = lock(workspaceId);
        CreditHold hold = requireHold(workspaceId, holdId);
        if (hold.getStatus() != CreditHoldStatus.REFUNDED) {
            hold.settle(CreditHoldStatus.CAPTURED, CreditHoldStatus.REFUNDED);
            returnToGrants(hold, balance, CreditEntryKind.REFUND);
        }
        return hold.receipt();
    }

    @Transactional
    public CreditGrantReceipt grant(CreditGrantCommand command) {
        CreditBalance balance = lock(command.workspaceId());
        if (command.externalRef() != null) {
            Optional<CreditGrant> existing = grants.findByWorkspaceIdAndSourceAndExternalRef(
                    command.workspaceId(), command.source(), command.externalRef());
            if (existing.isPresent()) {
                return receiptOf(existing.get(), true);
            }
        }
        Instant now = clock.instant();
        CreditGrant grant = grants.saveAndFlush(CreditGrant.issued(command, now));
        record(CreditEntry.granted(grant, now), balance);
        return receiptOf(grant, false);
    }

    @Transactional(readOnly = true)
    public CreditBalanceSummary balanceOf(UUID workspaceId) {
        return balances.findById(workspaceId)
                .map(balance -> new CreditBalanceSummary(balance.getAvailable(), balance.getHeld()))
                .orElse(new CreditBalanceSummary(0, 0));
    }

    private CreditBalance lock(UUID workspaceId) {
        balances.openIfAbsent(workspaceId);
        return balances.findForUpdate(workspaceId)
                .orElseThrow(() -> new IllegalStateException("no balance row for workspace " + workspaceId));
    }

    private CreditHold open(CreditCharge charge, CreditBalance balance) {
        Instant now = clock.instant();
        long price = charge.action().priceIn(settings().prices());
        List<CreditGrant> spendable = grants.findSpendable(charge.workspaceId(), now);
        long coverable = spendable.stream().mapToLong(CreditGrant::getRemaining).sum();
        if (coverable < price && settings().enforce()) {
            throw insufficient(charge.workspaceId(), price, coverable);
        }

        CreditHold hold = holds.saveAndFlush(
                CreditHold.opened(charge, price, Math.min(price, coverable), now.plus(settings().holdTtl())));
        long outstanding = hold.getCovered();
        for (CreditGrant grant : spendable) {
            if (outstanding == 0) {
                break;
            }
            long taken = Math.min(outstanding, grant.getRemaining());
            grant.take(taken);
            record(CreditEntry.ofHold(hold, CreditEntryKind.HOLD, grant.getId(), -taken, taken, now), balance);
            outstanding -= taken;
        }
        return hold;
    }

    private void spend(CreditHold hold, CreditBalance balance) {
        hold.settle(CreditHoldStatus.OPEN, CreditHoldStatus.CAPTURED);
        Instant now = clock.instant();
        for (CreditEntry held : entries.findByHoldIdAndKindOrderById(hold.getId(), CreditEntryKind.HOLD)) {
            record(CreditEntry.ofHold(hold, CreditEntryKind.CAPTURE, held.getGrantId(), 0, -held.getHeldDelta(), now),
                    balance);
        }
        if (hold.overdraft() > 0) {
            record(CreditEntry.overdrawn(hold, now), balance);
        }
    }

    private void returnToGrants(CreditHold hold, CreditBalance balance, CreditEntryKind kind) {
        Instant now = clock.instant();
        for (CreditEntry held : entries.findByHoldIdAndKindOrderById(hold.getId(), CreditEntryKind.HOLD)) {
            long credits = held.getHeldDelta();
            grants.findById(held.getGrantId()).orElseThrow().giveBack(credits);
            long heldDelta = kind == CreditEntryKind.RELEASE ? -credits : 0;
            record(CreditEntry.ofHold(hold, kind, held.getGrantId(), credits, heldDelta, now), balance);
        }
    }

    private void record(CreditEntry entry, CreditBalance balance) {
        entries.save(entry);
        balance.apply(entry);
    }

    private CreditHold requireHold(UUID workspaceId, UUID holdId) {
        return holds.findByIdAndWorkspaceId(holdId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("no hold " + holdId + " in workspace " + workspaceId));
    }

    private ApiException insufficient(UUID workspaceId, long required, long available) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("required", required);
        body.put("available", available);
        subscriptions.findByWorkspaceId(workspaceId)
                .map(WorkspaceSubscription::getCurrentPeriodEnd)
                .ifPresent(resetsAt -> body.put("resetsAt", resetsAt));
        return ApiException.withProperties(ErrorCode.INSUFFICIENT_CREDITS, body);
    }

    private static CreditGrantReceipt receiptOf(CreditGrant grant, boolean alreadyGranted) {
        return new CreditGrantReceipt(grant.getId(), grant.getSource(), grant.getAmount(), grant.getExpiresAt(),
                alreadyGranted);
    }

    private BillingSettings settings() {
        return properties.billing();
    }
}
