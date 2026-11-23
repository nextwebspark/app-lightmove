package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.config.ApplicationVariable;
import app.lightmove.api.core.config.ApplicationVariables;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SourcingRunLimits;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Find executives' limits a platform super admin may change without a release. A run takes one set when it starts
 * and keeps it, so a change reaches the runs requested after it and never one in flight.
 */
@Component
@RequiredArgsConstructor
public class SourcingLimits {

    private final ApplicationVariables variables;

    public int maxCompaniesPerRun() {
        return variables.intValue(ApplicationVariable.SOURCING_MAX_COMPANIES_PER_RUN);
    }

    public Duration runDeadline() {
        return Duration.ofSeconds(variables.intValue(ApplicationVariable.SOURCING_RUN_DEADLINE));
    }

    public SourcingRunLimits current() {
        return new SourcingRunLimits(
                variables.intValue(ApplicationVariable.SOURCING_INDEX_PEOPLE_PER_COMPANY),
                variables.intValue(ApplicationVariable.SOURCING_TITLE_CHECKS_PER_COMPANY),
                variables.intValue(ApplicationVariable.SOURCING_PARALLELISM),
                runDeadline());
    }

    /** The current limits under the deadline the run was requested with: the one it is judged lost against. */
    public SourcingRunLimits forRun(ExecutiveSourcingRun run) {
        SourcingRunLimits current = current();
        return new SourcingRunLimits(current.indexPeoplePerCompany(), current.titleChecksPerCompany(),
                current.parallelism(), run.runDeadline().orElse(current.runDeadline()));
    }

    /** For a run that recorded no deadline of its own, and for a screen that has no run yet. */
    public Duration lostAfter() {
        return runDeadline().plus(ExecutiveSourcingRun.LOST_GRACE);
    }
}
