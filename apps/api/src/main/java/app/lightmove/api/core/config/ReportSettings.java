package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much one read of a mandate's report may carry ({@code lightmove.report.*}): two row caps stand in for a pager and
 * the response says when one hit; the rest fold a chapter's tail into "Other"; {@code maxRanked} caps the Ranking chapter.
 */
public record ReportSettings(
        @DefaultValue("2000") int maxCompanies,
        @DefaultValue("5000") int maxCandidates,
        @DefaultValue("6") int maxSectors,
        @DefaultValue("8") int maxHubs,
        @DefaultValue("11") int maxNationalities,
        @DefaultValue("3") int maxEmployersPerHub,
        @DefaultValue("12") int maxExecutivesPerSlice,
        @DefaultValue("250") int maxRanked
) {}
