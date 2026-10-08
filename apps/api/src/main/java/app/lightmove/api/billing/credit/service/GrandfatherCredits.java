package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.plan.service.BillingWorkspaces;
import app.lightmove.api.core.config.GrandfatherSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Gives every workspace that exists when enforcement first boots its one-off promotional credits, keyed
 * {@code grandfather:<workspace>}. The first instance to claim the run grants; a workspace founded later never
 * gets them. A run that fails anywhere gives its claim back, so the next boot finishes it without granting twice.
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

    /** @return how many workspaces this call granted to */
    public int grantAt(Instant now) {
        GrandfatherSettings settings = properties.billing().grandfather();
        if (settings.credits() <= 0 || !jobRuns.claimForGood(JOB, RUN_KEY)) {
            return 0;
        }
        int granted = 0;
        boolean failed = false;
        try {
            for (UUID workspaceId : workspaces.activeIds()) {
                try {
                    granted += grant(workspaceId, settings, now) ? 1 : 0;
                } catch (RuntimeException failure) {
                    failed = true;
                    log.error("Could not grant the grandfathered credits of workspace {}", workspaceId, failure);
                }
            }
        } catch (RuntimeException failure) {
            failed = true;
            log.error("Could not list the workspaces to grandfather", failure);
        }
        if (failed) {
            jobRuns.giveBack(JOB, RUN_KEY);
        }
        log.info("Granted grandfathered contact credits to {} workspaces", granted);
        return granted;
    }

    private boolean grant(UUID workspaceId, GrandfatherSettings settings, Instant now) {
        return !ledger.grant(new CreditGrantCommand(workspaceId, CreditGrantSource.PROMO, settings.credits(), now,
                now.plus(settings.validFor()), BigDecimal.ZERO, grantKey(workspaceId), null,
                "Given to every workspace when contact credits began to be enforced")).alreadyGranted();
    }
}
