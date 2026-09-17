package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much one read of a mandate's report may carry — {@code lightmove.report.*}.
 *
 * <p>The report aggregates every company and executive of a mandate in one read, so the two row caps
 * are what a pager would otherwise be; the response says when it hit one. The remaining caps bound
 * how many categories a chapter names before folding the tail into "Other", which is a readability
 * limit rather than a cost one.
 */
public record ReportSettings(
        @DefaultValue("2000") int maxCompanies,
        @DefaultValue("5000") int maxCandidates,
        @DefaultValue("6") int maxSectors,
        @DefaultValue("8") int maxHubs,
        @DefaultValue("9") int maxNationalities,
        @DefaultValue("3") int maxEmployersPerHub,
        @DefaultValue("12") int maxExecutivesPerSlice
) {}
