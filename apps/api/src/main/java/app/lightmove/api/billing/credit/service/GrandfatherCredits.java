package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.plan.service.BillingWorkspaces;
import app.lightmove.api.core.config.GrandfatherSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Gives every workspace that exists the first time a release boots with enforcement on its one-off promotional
 * credits, keyed {@code grandfather:<workspace>}. The first instance to claim the run grants; a workspace founded
 * later never gets them. A run that fails anywhere gives its claim back, so the next boot finishes it without
 * granting twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrandfatherCredits implements ApplicationRunner {

    static final String JOB = "grandfather-credits";
    static final String RUN_KEY = "once";

    private final BillingJobRuns jobRuns;
    private final BillingWorkspaces workspaces;
    private final CreditLedger ledger;
    private final LightMoveProperties properties;
    private final Clock clock;

    public static String grantKey(UUID workspaceId) {
        return "grandfather:" + workspaceId;
    }

    @Override
    public void run(ApplicationArguments args) {
        grantAt(clock.instant());
    }

    /** @return how many workspaces this call granted to; none while enforcement is off or the size is 0 */
    public int grantAt(Instant now) {
        GrandfatherSettings settings = properties.billing().grandfather();
        if (!properties.billing().enforce() || settings.credits() <= 0 || !jobRuns.claimForGood(JOB, RUN_KEY)) {
            return 0;
        }
        List<UUID> workspaceIds;
        try {
            workspaceIds = workspaces.activeIds();
        } catch (RuntimeException failure) {
            jobRuns.giveBack(JOB, RUN_KEY);
            log.error("Could not list the workspaces to grandfather; the next boot retries", failure);
            return 0;
        }
        log.info("Grandfathering {} workspaces with {} contact credits valid for {}", workspaceIds.size(),
                settings.credits(), settings.validFor());
        int granted = 0;
        int failed = 0;
        for (UUID workspaceId : workspaceIds) {
            try {
                granted += grant(workspaceId, settings, now) ? 1 : 0;
            } catch (RuntimeException failure) {
                failed++;
                log.error("Could not grant the grandfathered credits of workspace {}", workspaceId, failure);
            }
        }
        if (failed > 0) {
            jobRuns.giveBack(JOB, RUN_KEY);
            log.error("Grandfathering failed for {} workspaces after granting {}; the next boot retries", failed,
                    granted);
        } else {
            log.info("Granted grandfathered contact credits to {} workspaces", granted);
        }
        return granted;
    }

    private boolean grant(UUID workspaceId, GrandfatherSettings settings, Instant now) {
        return !ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PROMO, settings.credits(), now,
                now.plus(settings.validFor()), BigDecimal.ZERO, grantKey(workspaceId), null,
                "Given to every workspace when contact credits began to be enforced")).alreadyGranted();
    }
}
