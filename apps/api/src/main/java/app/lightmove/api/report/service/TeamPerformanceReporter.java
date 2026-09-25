package app.lightmove.api.report.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateContactsDto;
import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import app.lightmove.api.report.constant.ResearcherRole;
import app.lightmove.api.report.constant.SourcingQualityLevel;
import app.lightmove.api.report.dto.CompanyCoverageDto;
import app.lightmove.api.report.dto.CoverageShareDto;
import app.lightmove.api.report.dto.ResearcherDto;
import app.lightmove.api.report.dto.SourcedExecutiveDto;
import app.lightmove.api.report.dto.SourcingQualityDto;
import app.lightmove.api.report.dto.StatusCountDto;
import app.lightmove.api.report.dto.TeamKpisDto;
import app.lightmove.api.report.dto.TeamPerformanceDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportCalendar;
import app.lightmove.api.report.model.ReportRange;
import app.lightmove.api.report.model.ReportSources;
import app.lightmove.api.report.model.ResearcherIdentity;
import app.lightmove.api.report.model.TeamSources;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chapter one's researcher breakdown, attributed by {@code added_by}. It states only what the rows
 * carry: a status is where someone stands now, never a conversion step, and quality is what is on
 * file — there is no confidence score and no per-company target to report against.
 *
 * <p>A company's coverage is credited to whoever filed its <i>first</i> executive, the same moment
 * the coverage line counts it mapped, so the donut's slices add up to the chapter's own figure.
 */
@Component
class TeamPerformanceReporter {

    private static final int MAX_COMPANIES = 24;
    private static final int MAX_RECENT = 5;
    private static final int GOOD_QUALITY_FLOOR = 50;
    private static final double DAYS_PER_WEEK = 7.0;

    TeamPerformanceDto report(ReportSources sources, ReportCalendar calendar, ReportRange range, TeamSources team) {
        List<ExecutiveRow> all = sources.executives();
        List<ExecutiveRow> inRange = all.stream()
                .filter(row -> range.contains(ReportCalendar.dateOf(row.mappedAt())))
                .toList();
        Map<UUID, String> nameOf = team.researchers().values().stream()
                .collect(Collectors.toMap(ResearcherIdentity::userId, ResearcherIdentity::name));

        Map<UUID, UUID> coverageCredit = coverageCredit(all, team);
        ExecutiveRow latest = all.stream().max(Comparator.comparing(ExecutiveRow::mappedAt)).orElse(null);
        TeamKpisDto kpis = new TeamKpisDto(
                inRange.size(),
                inRange.size() / (double) range.days() * DAYS_PER_WEEK,
                all.size() / (double) calendar.dayCount() * DAYS_PER_WEEK,
                coverageCredit.size(),
                sources.universeTotal(),
                latest == null ? null : latest.mappedAt(),
                latest == null ? null : nameOf.get(addedBy(latest, team)));

        List<ResearcherDto> researchers = team.researchers().values().stream()
                .map(identity -> researcher(identity, all, inRange, range, team, nameOf))
                .filter(row -> row.role() != ResearcherRole.FORMER || row.executives() > 0)
                .sorted(Comparator.comparingInt(ResearcherDto::executives).reversed())
                .toList();

        List<CompanyCoverageDto> companies = companies(sources.universe(), all, team, nameOf);
        return new TeamPerformanceDto(range.from(), range.to(), range.days(), sources.isTruncated(), kpis,
                coverage(coverageCredit, nameOf), researchers,
                companies.stream().limit(MAX_COMPANIES).toList(), companies.size());
    }

    private static ResearcherDto researcher(ResearcherIdentity identity, List<ExecutiveRow> all,
                                            List<ExecutiveRow> inRange, ReportRange range, TeamSources team,
                                            Map<UUID, String> nameOf) {
        Predicate<ExecutiveRow> theirs = row -> identity.userId().equals(addedBy(row, team));
        List<ExecutiveRow> filed = inRange.stream().filter(theirs).toList();
        Instant lastAddedAt = all.stream().filter(theirs).map(ExecutiveRow::mappedAt)
                .max(Instant::compareTo).orElse(null);
        int companies = (int) filed.stream().filter(ExecutiveRow::hasCompany)
                .map(row -> row.company().id()).distinct().count();

        int[] daily = new int[range.days()];
        filed.forEach(row -> daily[range.indexOf(ReportCalendar.dateOf(row.mappedAt()))]++);

        return new ResearcherDto(identity.userId(), identity.name(), identity.avatarUrl(), identity.role(),
                filed.size(), companies, filed.size() / (double) range.days(), percent(filed.size(), inRange.size()),
                lastAddedAt, filed.isEmpty() ? null : quality(filed), statusMix(filed),
                Arrays.stream(daily).boxed().toList(), newestFirst(filed, MAX_RECENT, team, nameOf));
    }

    private static List<CompanyCoverageDto> companies(List<TriageCompanyResponse> universe, List<ExecutiveRow> all,
                                                      TeamSources team, Map<UUID, String> nameOf) {
        Map<UUID, List<ExecutiveRow>> byCompany = all.stream().filter(ExecutiveRow::hasCompany)
                .collect(Collectors.groupingBy(row -> row.company().id()));
        return universe.stream()
                .filter(company -> byCompany.containsKey(company.id()))
                .map(company -> {
                    List<ExecutiveRow> mapped = byCompany.get(company.id());
                    int contributors = (int) mapped.stream().map(row -> addedBy(row, team))
                            .filter(Objects::nonNull).distinct().count();
                    Instant last = mapped.stream().map(ExecutiveRow::mappedAt).max(Instant::compareTo).orElse(null);
                    return new CompanyCoverageDto(company.id(), company.companyName(), company.industry(),
                            company.companyCity(), company.companyCountry(), company.numEmployees(), company.status(),
                            mapped.size(), contributors, last, statusMix(mapped), quality(mapped),
                            newestFirst(mapped, Integer.MAX_VALUE, team, nameOf));
                })
                .sorted(Comparator.comparingInt(CompanyCoverageDto::executives).reversed()
                        .thenComparing(CompanyCoverageDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Company id → the user who filed its first executive, for every universe company that has one. */
    private static Map<UUID, UUID> coverageCredit(List<ExecutiveRow> all, TeamSources team) {
        Map<UUID, ExecutiveRow> first = new HashMap<>();
        for (ExecutiveRow row : all) {
            if (row.hasCompany()) {
                first.merge(row.company().id(), row,
                        (kept, next) -> next.mappedAt().isBefore(kept.mappedAt()) ? next : kept);
            }
        }
        Map<UUID, UUID> credit = new HashMap<>();
        first.forEach((companyId, row) -> credit.put(companyId, addedBy(row, team)));
        return credit;
    }

    private static List<CoverageShareDto> coverage(Map<UUID, UUID> coverageCredit, Map<UUID, String> nameOf) {
        Map<UUID, Integer> perUser = new LinkedHashMap<>();
        coverageCredit.values().stream().filter(Objects::nonNull).forEach(user -> perUser.merge(user, 1, Integer::sum));
        return perUser.entrySet().stream()
                .map(entry -> new CoverageShareDto(entry.getKey(), nameOf.getOrDefault(entry.getKey(), ""), entry.getValue()))
                .sorted(Comparator.comparingInt(CoverageShareDto::companies).reversed()
                        .thenComparing(CoverageShareDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static SourcingQualityDto quality(List<ExecutiveRow> rows) {
        int contact = percent(count(rows, TeamPerformanceReporter::hasContact), rows.size());
        int verified = percent(count(rows, TeamPerformanceReporter::hasVerifiedContact), rows.size());
        int comp = percent(count(rows, TeamPerformanceReporter::hasBaseSalary), rows.size());
        SourcingQualityLevel level = (contact + verified + comp) / 3.0 >= GOOD_QUALITY_FLOOR
                ? SourcingQualityLevel.GOOD : SourcingQualityLevel.ATTENTION;
        return new SourcingQualityDto(contact, verified, comp, level);
    }

    /** Current statuses in the enum's own order, zero rows left out. */
    private static List<StatusCountDto> statusMix(List<ExecutiveRow> rows) {
        Map<CandidateStatus, Long> counts = rows.stream().map(ExecutiveRow::status).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(status -> status, Collectors.counting()));
        return Arrays.stream(CandidateStatus.values())
                .filter(counts::containsKey)
                .map(status -> new StatusCountDto(status.value(), counts.get(status).intValue()))
                .toList();
    }

    private static List<SourcedExecutiveDto> newestFirst(List<ExecutiveRow> rows, int limit, TeamSources team,
                                                         Map<UUID, String> nameOf) {
        return rows.stream()
                .sorted(Comparator.comparing(ExecutiveRow::mappedAt).reversed())
                .limit(limit)
                .map(row -> new SourcedExecutiveDto(row.executive().id(), row.executive().fullName(),
                        row.executive().title(), row.executive().seniority(), row.employerName(),
                        row.executive().status(), nameOf.get(addedBy(row, team)), row.mappedAt()))
                .toList();
    }

    private static UUID addedBy(ExecutiveRow row, TeamSources team) {
        return team.addedByCandidate().get(row.executive().id());
    }

    private static boolean hasContact(ExecutiveRow row) {
        CandidateContactsDto contacts = row.executive().contacts();
        return contacts != null && !(contacts.emails().isEmpty() && contacts.phones().isEmpty());
    }

    private static boolean hasVerifiedContact(ExecutiveRow row) {
        CandidateContactsDto contacts = row.executive().contacts();
        return contacts != null && (contacts.emails().stream().anyMatch(CandidateEmailDto::verified)
                || contacts.phones().stream().anyMatch(CandidatePhoneDto::verified));
    }

    private static boolean hasBaseSalary(ExecutiveRow row) {
        CandidateCompensationDto compensation = row.executive().compensation();
        return compensation != null && compensation.baseSalary() != null;
    }

    private static int count(List<ExecutiveRow> rows, Predicate<ExecutiveRow> test) {
        return (int) rows.stream().filter(test).count();
    }

    private static int percent(int part, int whole) {
        return whole == 0 ? 0 : Math.round(part * 100f / whole);
    }
}
