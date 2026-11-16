package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fair use for an import's paid reads, checked just before each vendor call and recorded once it is made. Soft by up to
 * the reads in flight, as every lock-free fair-use check is; a read whose vendor call throws is not recorded.
 */
@Component
@RequiredArgsConstructor
class ImportEnrichmentAllowance {

    private final FairUseGuard fairUse;
    private final UsageRecorder usage;

    void requirePaidRead(ClaimedEnrichmentItem item) {
        try {
            fairUse.check(item.workspaceId(), item.requestedBy(), UsageKind.PROFILE_ENRICH, 1);
        } catch (ApiException fairUseReachedOrTrialEnded) {
            throw new ImportAllowanceSpent();
        }
    }

    void recordPaidRead(ClaimedEnrichmentItem item) {
        usage.record(MeteredUse.of(item.workspaceId(), item.requestedBy(), item.projectId(),
                UsageKind.PROFILE_ENRICH, 1));
    }
}
