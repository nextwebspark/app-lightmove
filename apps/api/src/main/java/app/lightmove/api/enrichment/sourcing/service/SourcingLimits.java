package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.config.ApplicationVariable;
import app.lightmove.api.core.config.ApplicationVariables;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Find executives' limits a platform super admin may change without a release. Read at each use, never held: a
 * change reaches a run started after it.
 */
@Component
@RequiredArgsConstructor
public class SourcingLimits {

    private final ApplicationVariables variables;

    /** Companies one run may take; the screen offers that many when nothing is ticked. */
    public int maxCompaniesPerRun() {
        return variables.intValue(ApplicationVariable.SOURCING_MAX_COMPANIES_PER_RUN);
    }

    /** People the profile index returns per company to be checked. */
    public int indexPeoplePerCompany() {
        return variables.intValue(ApplicationVariable.SOURCING_INDEX_PEOPLE_PER_COMPANY);
    }

    /** Free name-narrowed live counts per company, each confirming one index person's title. */
    public int titleChecksPerCompany() {
        return variables.intValue(ApplicationVariable.SOURCING_TITLE_CHECKS_PER_COMPANY);
    }

    /** Companies searched at once. */
    public int parallelism() {
        return variables.intValue(ApplicationVariable.SOURCING_PARALLELISM);
    }

    /** How long the whole run may take before the companies not reached are reported as such. */
    public Duration runDeadline() {
        return Duration.ofSeconds(variables.intValue(ApplicationVariable.SOURCING_RUN_DEADLINE));
    }

    /** Past this a run still marked in progress was lost with its instance: the deadline, and a minute's grace. */
    public Duration lostAfter() {
        return runDeadline().plusMinutes(1);
    }
}
