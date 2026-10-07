package app.lightmove.api.billing.usage.service;

import app.lightmove.api.billing.plan.service.BillingSeats;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.FairUseAllowance;
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
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;
    private final Cache<String, Boolean> reported;

    public FairUseGuard(UsageEventStore events, BillingSeats seats, AuditService audit,
                        LightMoveProperties properties, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.reported = Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).maximumSize(10_000).build();
    }

    /** @throws ApiException {@code FAIR_USE_REACHED}, carrying {@code kind} and {@code resetsAt} */
    public void check(UUID workspaceId, UUID userId, UsageKind kind, long units) {
        long perSeat = kind.perSeatIn(properties.billing().fairUse());
        if (perSeat == 0) {
            return;
        }
        FairUseAllowance allowance = seats.allowanceOf(workspaceId, clock.instant());
        long ceiling = perSeat * allowance.staffSeats();
        long used = events.unitsBetween(workspaceId, kind, allowance.periodStart(), allowance.periodEnd());
        if (used + units <= ceiling) {
            return;
        }
        reportOnce(workspaceId, userId, kind, used, ceiling, allowance.periodEnd());
        throw ApiException.withProperties(ErrorCode.FAIR_USE_REACHED,
                Map.of("kind", kind.name(), "resetsAt", allowance.periodEnd()));
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
