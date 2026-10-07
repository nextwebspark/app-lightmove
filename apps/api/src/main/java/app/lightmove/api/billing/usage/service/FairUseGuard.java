package app.lightmove.api.billing.usage.service;

import app.lightmove.api.billing.plan.service.BillingSeats;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.FairUsePeriod;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Refuses an unpriced action that would take a workspace past its month's ceiling — seats × the kind's
 * {@code *-per-seat} — before any vendor or model is asked. Counted without a lock: two presses racing past the
 * line overshoot it by one, which a ceiling this generous absorbs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FairUseGuard {

    private final UsageEventStore events;
    private final BillingSeats seats;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final Clock clock;

    /** @throws ApiException {@code FAIR_USE_REACHED}, carrying {@code kind} and {@code resetsAt} */
    public void check(UUID workspaceId, UUID userId, UsageKind kind, long units) {
        long perSeat = kind.perSeatIn(properties.billing().fairUse());
        if (perSeat == 0) {
            return;
        }
        long ceiling = perSeat * seats.staffSeatsOf(workspaceId);
        FairUsePeriod period = seats.periodOf(workspaceId, clock.instant());
        long used = events.unitsBetween(workspaceId, kind, period.start(), period.end());
        if (used + units <= ceiling) {
            return;
        }
        log.warn("Fair use reached: workspace {} asked {} {} with {} of {} used this period", workspaceId, units,
                kind, used, ceiling);
        audit.event(WorkspaceEventType.FAIR_USE_REACHED).actor(userId).workspace(workspaceId)
                .target("workspace", workspaceId)
                .detail("kind", kind.name())
                .detail("used", used)
                .detail("ceiling", ceiling)
                .record();
        throw ApiException.withProperties(ErrorCode.FAIR_USE_REACHED,
                Map.of("kind", kind.name(), "resetsAt", period.end()));
    }
}
