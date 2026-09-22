package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * One mandate's mapping coverage: how many companies its live universe holds, and how many of those
 * anyone has actually researched. A Spring Data projection rather than a record because the query
 * behind it is native — {@code count(… ) filter (where …)} is Postgres's, and JPQL has no {@code new}
 * for a native result.
 */
public interface CompanyCoverageCount {

    UUID getProjectId();

    long getUniverseTotal();

    long getResearched();
}
