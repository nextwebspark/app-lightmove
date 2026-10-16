package app.uncava.api.billing.usage.service;

import app.uncava.api.billing.usage.model.MeteredUse;
import app.uncava.api.core.config.UncavaProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Records a use once the work has succeeded. Called outside any transaction, and never fails the work it meters:
 * a lost line under-counts fair use, where a thrown one would answer an error over a result already paid for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageRecorder {

    private final UsageEventStore events;
    private final UncavaProperties properties;

    public void record(MeteredUse use) {
        long estCostFils = use.units() * use.kind().costFilsIn(properties.billing().fairUse());
        try {
            events.insert(use, estCostFils);
        } catch (RuntimeException failed) {
            log.error("Could not record {} {} for workspace {}", use.units(), use.kind(), use.workspaceId(), failed);
        }
    }

    /** Whether a keyed use was already counted for this workspace. */
    public boolean hasRecorded(UUID workspaceId, String idempotencyKey) {
        return events.contains(workspaceId, idempotencyKey);
    }
}
