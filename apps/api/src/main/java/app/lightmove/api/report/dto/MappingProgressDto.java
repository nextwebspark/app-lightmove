package app.lightmove.api.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Chapter one. A company counts as mapped from its first executive's filing. Every series is indexed
 * from kickoff up to {@code asOf}; {@code daysSinceLastExecutive} is null until one is mapped.
 */
public record MappingProgressDto(
        LocalDate kickoff,
        LocalDate targetDate,
        LocalDate asOf,
        long targetCompanies,
        List<Integer> companiesCumulative,
        List<WeeklyCountDto> weekly,
        List<Integer> daily,
        Integer daysSinceLastExecutive
) {}
