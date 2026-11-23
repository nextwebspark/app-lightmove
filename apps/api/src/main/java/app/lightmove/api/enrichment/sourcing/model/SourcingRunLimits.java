package app.lightmove.api.enrichment.sourcing.model;

import java.time.Duration;

/** The limits one Find executives run works under from start to finish, whatever a super admin changes meanwhile. */
public record SourcingRunLimits(
        int indexPeoplePerCompany,
        int titleChecksPerCompany,
        int parallelism,
        Duration runDeadline
) {
}
