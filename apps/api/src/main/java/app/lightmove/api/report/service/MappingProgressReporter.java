package app.lightmove.api.report.service;

import app.lightmove.api.report.dto.MappingProgressDto;
import app.lightmove.api.report.dto.WeeklyCountDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportCalendar;
import app.lightmove.api.report.model.ReportSources;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Chapter one. Two series off one column: when each executive was filed gives the weekly and daily
 * momentum, and the earliest filing per company gives the day that company became mapped, which the
 * cumulative coverage climbs by. A company is mapped by its first executive, not by being triaged —
 * a universe of forty companies nobody has researched is forty companies still to map.
 */
@Component
class MappingProgressReporter {

    MappingProgressDto report(ReportSources sources, ReportCalendar calendar) {
        Collection<Instant> firstMappedAt = firstMappingPerCompany(sources.executives()).values();
        List<Instant> mappedAt = sources.executives().stream().map(ExecutiveRow::mappedAt).toList();

        int[] companiesPerWeek = histogram(firstMappedAt, calendar.weekCount(), calendar::weekIndexOf);
        int[] identifiedPerWeek = histogram(mappedAt, calendar.weekCount(), calendar::weekIndexOf);
        int[] identifiedPerDay = histogram(mappedAt, calendar.dayCount(), calendar::dayIndexOf);

        List<WeeklyCountDto> weekly = IntStream.range(0, identifiedPerWeek.length)
                .mapToObj(week -> new WeeklyCountDto(calendar.endOfWeek(week), identifiedPerWeek[week]))
                .toList();
        Integer daysSinceLastCompany = firstMappedAt.stream().max(Instant::compareTo)
                .map(latest -> calendar.daysBetween(latest, calendar.asOf()))
                .orElse(null);

        return new MappingProgressDto(calendar.kickoff(), calendar.targetDate(), calendar.asOf(),
                sources.universeTotal(), boxed(cumulative(companiesPerWeek)), weekly, boxed(identifiedPerDay),
                daysSinceLastCompany);
    }

    private static Map<UUID, Instant> firstMappingPerCompany(List<ExecutiveRow> executives) {
        Map<UUID, Instant> earliest = new HashMap<>();
        for (ExecutiveRow row : executives) {
            if (row.hasCompany()) {
                earliest.merge(row.company().id(), row.mappedAt(), (first, next) -> first.isBefore(next) ? first : next);
            }
        }
        return earliest;
    }

    private static int[] histogram(Collection<Instant> moments, int buckets, ToIntFunction<Instant> bucketOf) {
        int[] counts = new int[buckets];
        moments.forEach(moment -> counts[bucketOf.applyAsInt(moment)]++);
        return counts;
    }

    private static int[] cumulative(int[] perBucket) {
        int[] running = new int[perBucket.length];
        int sum = 0;
        for (int bucket = 0; bucket < perBucket.length; bucket++) {
            sum += perBucket[bucket];
            running[bucket] = sum;
        }
        return running;
    }

    private static List<Integer> boxed(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }
}
