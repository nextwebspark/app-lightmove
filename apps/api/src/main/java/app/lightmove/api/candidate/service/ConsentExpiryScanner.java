package app.lightmove.api.candidate.service;

import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The daily consent pass (#1267), a workspace at a time: requests and consents past their date close as expired, then
 * keep-on-file consents near their end are asked about again. Only the workspaces V223's resolver names are walked.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class ConsentExpiryScanner {

    private final TenantResolvers resolvers;
    private final EveryWorkspace workspaces;
    private final PersonConsentService consents;
    private final Clock clock;

    @Scheduled(cron = "${lightmove.data-protection.consent-scan}", zone = "UTC")
    public void scheduledScan() {
        scanAt(clock.instant());
    }

    /** Each step in its own transaction, so a renewal that fails never undoes what closed. */
    public int scanAt(Instant now) {
        List<UUID> due = resolvers.workspacesWithConsentsDue(now);
        return workspaces.sum(due, workspaceId -> consents.closeLapsed(workspaceId, now)
                + consents.requestRenewals(workspaceId, now));
    }
}
