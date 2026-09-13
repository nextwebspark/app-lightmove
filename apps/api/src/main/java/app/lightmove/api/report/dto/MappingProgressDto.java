package app.lightmove.api.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Chapter one: how far the mapping has got, week by week from kickoff. A company counts as mapped
 * from the day its first executive was filed, so {@code companiesCumulative} climbs towards
 * {@code targetCompanies}, the universe as it stands.
 *
 * <p>Every series is indexed from kickoff — week 0 and day 0 are the kickoff's own — up to
 * {@code asOf}, so the screen can project from them without asking when each one started.
 * {@code daysSinceLastCompany} is null while no company has an executive yet.
 */
public record MappingProgressDto(
        LocalDate kickoff,
        LocalDate targetDate,
        LocalDate asOf,
        long targetCompanies,
        List<Integer> companiesCumulative,
        List<WeeklyCountDto> weekly,
        List<Integer> daily,
        Integer daysSinceLastCompany
) {}
