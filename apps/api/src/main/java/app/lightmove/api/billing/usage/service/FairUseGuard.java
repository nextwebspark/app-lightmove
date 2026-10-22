package app.lightmove.api.billing.usage.service;

import app.lightmove.api.billing.plan.service.BillingSeats;
import app.lightmove.api.billing.plan.service.TrialGate;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.FairUseAllowance;
import app.lightmove.api.billing.usage.model.FairUseSpent;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Refuses an unpriced action that would take a workspace past its month's ceiling — seats × the kind's
 * {@code *-per-seat} — before any vendor or model is asked. Counted without a lock, and a use is recorded only once
 * its work succeeds, so presses racing past the line each pass: the overshoot is up to their number × their units,
 * which a ceiling this generous absorbs.
 *
 * <p>The first refusal per workspace, kind and period on each instance is audited and logged; the retries after it
 * are only answered, so a client retrying a 429 cannot flood the audit trail.
 */
@Slf4j
@Service
public class FairUseGuard {

    private final UsageEventStore events;
    private final BillingSeats seats;
    private final TrialGate trialGate;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;
    private final Cache<String, Boolean> reported;

    public FairUseGuard(UsageEventStore events, BillingSeats seats, TrialGate trialGate, AuditService audit,
                        LightMoveProperties properties, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.trialGate = trialGate;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.reported = Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).maximumSize(10_000).build();
    }

    /**
     * @throws ApiException {@code TRIAL_ENDED} once an unpaid trial has, or {@code FAIR_USE_REACHED}, carrying
     *                      {@code kind} and {@code resetsAt}
     */
    public void check(UUID workspaceId, UUID userId, UsageKind kind, long units) {
        trialGate.requireOpen(workspaceId);
        if (kind.perSeatIn(properties.billing().fairUse()) == 0) {
            return;
        }
        FairUseAllowance allowance = seats.allowanceOf(workspaceId, clock.instant());
        FairUseSpent spent = spentIn(workspaceId, kind, allowance);
        if (!spent.passedBy(units)) {
            return;
        }
        reportOnce(workspaceId, userId, kind, spent.used(), spent.ceiling(), allowance.periodEnd());
        throw ApiException.withProperties(ErrorCode.FAIR_USE_REACHED,
                Map.of("kind", kind.name(), "resetsAt", allowance.periodEnd()));
    }

    /**
     * Why {@link #check} would refuse, without refusing: for a caller that skips the work rather than failing a
     * request (the Notetaker's scheduler). Nothing is audited.
     */
    public Optional<ErrorCode> refusalOf(UUID workspaceId, UsageKind kind, long units) {
        if (trialGate.hasEnded(workspaceId)) {
            return Optional.of(ErrorCode.TRIAL_ENDED);
        }
        if (kind.perSeatIn(properties.billing().fairUse()) == 0) {
            return Optional.empty();
        }
        return spentOf(workspaceId, kind).passedBy(units) ? Optional.of(ErrorCode.FAIR_USE_REACHED)
                : Optional.empty();
    }

    /** What a workspace has used of one kind this billing month, beside its ceiling. */
    public FairUseSpent spentOf(UUID workspaceId, UsageKind kind) {
        return spentIn(workspaceId, kind, seats.allowanceOf(workspaceId, clock.instant()));
    }

    private FairUseSpent spentIn(UUID workspaceId, UsageKind kind, FairUseAllowance allowance) {
        long used = events.unitsBetween(workspaceId, kind, allowance.periodStart(), allowance.periodEnd());
        long perSeat = kind.perSeatIn(properties.billing().fairUse());
        return new FairUseSpent(used, perSeat == 0 ? null : perSeat * allowance.staffSeats());
    }

    private void reportOnce(UUID workspaceId, UUID userId, UsageKind kind, long used, long ceiling, Instant resetsAt) {
        String key = workspaceId + ":" + kind + ":" + resetsAt;
        if (reported.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        log.warn("Fair use reached: workspace {} has used {} of {} {} this period", workspaceId, used, ceiling, kind);
        audit.event(WorkspaceEventType.FAIR_USE_REACHED).actor(userId).workspace(workspaceId)
                .target("workspace", workspaceId)
                .detail("kind", kind.name())
                .detail("used", used)
                .detail("ceiling", ceiling)
                .record();
    }
}
