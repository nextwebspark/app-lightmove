package app.lightmove.api.dashboard.service;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.dto.CandidatePipelineStageDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.dashboard.constant.ChartOptionKey;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.model.ChartChoice;
import app.lightmove.api.dashboard.model.ChartOption;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.model.ChartResult;
import app.lightmove.api.dashboard.model.ChartSeries;
import app.lightmove.api.report.dto.DisclosureDto;
import app.lightmove.api.report.dto.MappingProgressDto;
import app.lightmove.api.report.dto.MarketCellDto;
import app.lightmove.api.report.dto.MarketShapeDto;
import app.lightmove.api.report.dto.RemunerationDto;
import app.lightmove.api.report.dto.ReportResponse;
import app.lightmove.api.report.dto.TeamPerformanceDto;
import app.lightmove.api.report.dto.WeeklyCountDto;
import app.lightmove.api.report.service.ReportService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The position half of the library. Every figure comes through the report's own reads ({@code ReportService}) or the
 * mandate's executives as a staff reader sees them, so a chart never states anything the Overview could not.
 */
@Component
@RequiredArgsConstructor
class ProjectChartQueries {

    static final int EXECUTIVE_CAP = 5_000;
    static final int SMALLEST_SHOWN_GROUP = 3;

    private final ReportService reports;
    private final CandidateService candidates;

    List<ChartQuery> queries() {
        DashboardScope here = DashboardScope.PROJECT;
        return List.of(
                ChartQuery.kpi(here, "companiesMapped", "Mapping progress", "Companies mapped",
                        "Companies with at least one executive, against the scoped universe", this::companiesMapped),
                ChartQuery.kpi(here, "executivesIdentified", "Mapping progress", "Executives identified",
                        "Every executive mapped on this position", this::executivesIdentified),
                ChartQuery.kpi(here, "daysSinceLastExecutive", "Mapping progress", "Days since the last new executive",
                        "How long since anyone filed a new executive", this::daysSinceLastExecutive),
                ChartQuery.overTime(here, "executivesPerWeek", "Mapping progress", "Executives identified per week",
                        "New executives each week — not cumulative", ChartType.COLUMN, List.of(ChartOption.range()),
                        this::executivesPerWeek),
                ChartQuery.overTime(here, "companiesCoveredOverTime", "Mapping progress", "Companies covered over time",
                        "Cumulative companies with an executive, week by week", ChartType.AREA,
                        List.of(ChartOption.range()), this::companiesCoveredOverTime),
                ChartQuery.breakdown(here, "executivesBy", "Pipeline", "Executives by …",
                        "The mapped executives by stage, status, level, country or source", ChartType.BAR,
                        List.of(executiveGroups(), ChartOption.top()), this::executivesBy),
                ChartQuery.breakdownByTwo(here, "stageByLevel", "Pipeline", "Stage by level",
                        "Where each level sits in the pipeline", this::stageByLevel),
                ChartQuery.overTime(here, "statusChangesPerWeek", "Pipeline", "Executives moved per week",
                        "Executives whose status last changed in each week", ChartType.LINE,
                        List.of(ChartOption.range()), this::statusChangesPerWeek),
                ChartQuery.breakdown(here, "companiesBySector", "Market", "Companies by sector",
                        "The scoped universe by sector", ChartType.PIE, List.of(ChartOption.top()),
                        this::companiesBySector),
                ChartQuery.breakdown(here, "talentHubs", "Market", "Executives by country",
                        "Where the mapped talent sits, by country", ChartType.BAR, List.of(ChartOption.top()),
                        this::talentHubs),
                ChartQuery.breakdownByTwo(here, "sectorByLevel", "Market", "Sector by level",
                        "Executives in each sector at each level", this::sectorByLevel),
                ChartQuery.breakdown(here, "medianPackageBy", "Remuneration", "Median package by …",
                        "Disclosed total packages in the brief's currency, median per group — other currencies are "
                                + "not converted",
                        ChartType.COLUMN, List.of(packageGroups()), this::medianPackageBy).asNotAdditive(),
                ChartQuery.kpi(here, "packagesAboveBand", "Remuneration", "Packages above our band",
                        "Disclosed packages priced above the brief's package band", this::packagesAboveBand),
                ChartQuery.breakdownByTwo(here, "genderByLevel", "Diversity & DEI", "Gender by level",
                        "Recorded by a researcher only, never inferred — aggregate, no individual identifiable",
                        this::genderByLevel),
                ChartQuery.breakdown(here, "executivesPerResearcher", "Team", "Executives found per researcher",
                        "Who on the team filed which executives", ChartType.BAR, List.of(),
                        this::executivesPerResearcher));
    }

    private static ChartOption packageGroups() {
        return new ChartOption(ChartOptionKey.GROUP, "Group by",
                List.of(new ChartChoice("country", "Country"), new ChartChoice("status", "Status")), "country");
    }

    private static ChartOption executiveGroups() {
        return new ChartOption(ChartOptionKey.GROUP, "Group by", List.of(new ChartChoice("stage", "Stage"),
                new ChartChoice("status", "Status"), new ChartChoice("level", "Level"),
                new ChartChoice("country", "Country"), new ChartChoice("source", "Source")), "stage");
    }

    private ReportResponse report(ChartQueryContext context) {
        return context.sharedRead("report",
                () -> reports.read(context.workspaceId(), context.projectId(), ProjectAudience.STAFF));
    }

    private CandidatesResponse executives(ChartQueryContext context) {
        return context.sharedRead("executives", () -> candidates.listAllOfProject(context.workspaceId(),
                context.projectId(), EXECUTIVE_CAP, ProjectAudience.STAFF));
    }

    private ChartResult companiesMapped(ChartQueryContext context, Map<String, String> options) {
        MappingProgressDto progress = report(context).progress();
        List<Integer> cumulative = progress.companiesCumulative();
        int mapped = cumulative.isEmpty() ? 0 : cumulative.getLast();
        long target = progress.targetCompanies();
        String sub = target == 0 ? "no companies in scope yet" : ChartLabels.percent(mapped, target) + "% of the scoped universe";
        return ChartResult.kpi(String.valueOf(mapped), "/" + target, sub, false);
    }

    private ChartResult executivesIdentified(ChartQueryContext context, Map<String, String> options) {
        ReportResponse report = report(context);
        int weeks = report.progress().weekly().size();
        return ChartResult.kpi(String.valueOf(report.head().executivesMapped()), "",
                "across " + weeks + (weeks == 1 ? " week" : " weeks"), false);
    }

    private ChartResult daysSinceLastExecutive(ChartQueryContext context, Map<String, String> options) {
        Integer days = report(context).progress().daysSinceLastExecutive();
        return days == null
                ? ChartResult.kpi("—", "", "no executive filed yet", false)
                : ChartResult.kpi(String.valueOf(days), "d", "since the last one was filed", days >= 7);
    }

    private ChartResult executivesPerWeek(ChartQueryContext context, Map<String, String> options) {
        List<WeeklyCountDto> weekly = report(context).progress().weekly();
        return ChartResult.timeSeries("Week ending", "Executives",
                weekly.stream().map(week -> ChartLabels.WEEK_ENDING.format(week.weekEnding())).toList(),
                weekly.stream().map(week -> (double) week.identified()).toList());
    }

    private ChartResult companiesCoveredOverTime(ChartQueryContext context, Map<String, String> options) {
        MappingProgressDto progress = report(context).progress();
        int weeks = Math.min(progress.weekly().size(), progress.companiesCumulative().size());
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int week = 0; week < weeks; week++) {
            labels.add(ChartLabels.WEEK_ENDING.format(progress.weekly().get(week).weekEnding()));
            values.add((double) progress.companiesCumulative().get(week));
        }
        return ChartResult.timeSeries("Week ending", "Companies", labels, values);
    }

    private ChartResult executivesBy(ChartQueryContext context, Map<String, String> options) {
        CandidatesResponse found = executives(context);
        String group = options.get(ChartOptionKey.GROUP.key());
        ChartResult result = switch (group) {
            case "status" -> countBy("Status", found.candidates(), CandidateResponse::statusLabel, null);
            case "level" -> countBy("Level", found.candidates(), CandidateResponse::seniority, levelOrder());
            case "country" -> countBy("Country", found.candidates(), CandidateResponse::locationCountry, null);
            case "source" -> countBy("Source", found.candidates(), executive -> sourceLabel(executive.source()), null);
            default -> countBy("Stage", found.candidates(), ProjectChartQueries::stageLabel, stageOrder(found));
        };
        return result.withLabelsAndValues(result.labels(), result.values(), found.totalCount() > EXECUTIVE_CAP);
    }

    private ChartResult stageByLevel(ChartQueryContext context, Map<String, String> options) {
        CandidatesResponse found = executives(context);
        List<String> levels = presentLevels(found.candidates());
        List<String> stages = stageOrder(found);
        List<ChartSeries> series = new ArrayList<>();
        for (String stage : stages) {
            List<Double> values = new ArrayList<>();
            for (String level : levels) {
                values.add((double) found.candidates().stream()
                        .filter(executive -> stage.equals(stageLabel(executive))
                                && level.equals(ChartLabels.orNotRecorded(executive.seniority())))
                        .count());
            }
            series.add(new ChartSeries(stage, values));
        }
        return ChartResult.matrix("Level", levels, series, levels.stream().map(level -> false).toList(), false, null);
    }

    private ChartResult companiesBySector(ChartQueryContext context, Map<String, String> options) {
        MarketShapeDto market = report(context).market();
        return ChartResult.categorical("Sector", "Companies", "",
                market.companiesBySector().stream().map(row -> row.label()).toList(),
                market.companiesBySector().stream().map(row -> (double) row.count()).toList());
    }

    private ChartResult talentHubs(ChartQueryContext context, Map<String, String> options) {
        MarketShapeDto market = report(context).market();
        return ChartResult.categorical("Country", "Executives", "",
                market.hubs().stream().map(hub -> hub.country()).toList(),
                market.hubs().stream().map(hub -> (double) hub.count()).toList());
    }

    private ChartResult sectorByLevel(ChartQueryContext context, Map<String, String> options) {
        MarketShapeDto market = report(context).market();
        List<ChartSeries> series = new ArrayList<>();
        for (String level : market.levels()) {
            List<Double> values = new ArrayList<>();
            for (String sector : market.sectors()) {
                values.add(market.cells().stream()
                        .filter(cell -> cell.sector().equals(sector) && cell.level().equals(level))
                        .mapToDouble(MarketCellDto::count).sum());
            }
            series.add(new ChartSeries(level, values));
        }
        return ChartResult.matrix("Sector", market.sectors(), series,
                market.sectors().stream().map(sector -> false).toList(), false, null);
    }

    private ChartResult medianPackageBy(ChartQueryContext context, Map<String, String> options) {
        RemunerationDto remuneration = report(context).remuneration();
        Function<DisclosureDto, String> groupOf = "status".equals(options.get(ChartOptionKey.GROUP.key()))
                ? DisclosureDto::statusLabel
                : DisclosureDto::country;
        Map<String, List<Long>> packages = new LinkedHashMap<>();
        remuneration.disclosures().forEach(disclosure -> packages
                .computeIfAbsent(ChartLabels.orNotRecorded(groupOf.apply(disclosure)), key -> new ArrayList<>())
                .add(disclosure.totalPackage()));
        List<String> labels = new ArrayList<>(packages.keySet());
        List<Double> medians = labels.stream().map(label -> median(packages.get(label))).toList();
        String dimension = "status".equals(options.get(ChartOptionKey.GROUP.key())) ? "Status" : "Country";
        return ChartResult.categorical(dimension, "Median package", Objects.toString(remuneration.currency(), ""),
                labels, medians);
    }

    private ChartResult packagesAboveBand(ChartQueryContext context, Map<String, String> options) {
        RemunerationDto remuneration = report(context).remuneration();
        int disclosed = remuneration.disclosures().size();
        if (remuneration.packageBand() == null) {
            return ChartResult.kpi("—", "", "the brief sets no package band", false);
        }
        long ceiling = remuneration.packageBand().high();
        long above = remuneration.disclosures().stream().filter(disclosure -> disclosure.totalPackage() > ceiling)
                .count();
        String sub = disclosed == 0 ? "no package disclosed yet" : ChartLabels.percent(above, disclosed) + "% of disclosed packages";
        return ChartResult.kpi(String.valueOf(above), "/" + disclosed, sub, above > 0);
    }

    /**
     * Counts only a gender a researcher recorded or confirmed: one a model proposed and nobody changed is in
     * {@code aiInferredFields} and is left out. A level is held back and zeroed when it has fewer than
     * {@value #SMALLEST_SHOWN_GROUP} recorded or when any gender in it counts fewer than that, so nobody can be
     * singled out of a small group — two men and one woman would otherwise name the woman.
     */
    private ChartResult genderByLevel(ChartQueryContext context, Map<String, String> options) {
        List<CandidateResponse> recorded = executives(context).candidates().stream()
                .filter(executive -> Gender.fromValue(executive.gender()) != null)
                .filter(executive -> executive.aiInferredFields() == null
                        || !executive.aiInferredFields().contains("gender"))
                .toList();
        List<String> levels = presentLevels(recorded);
        List<Gender> genders = new ArrayList<>(List.of(Gender.FEMALE, Gender.MALE));
        if (recorded.stream().anyMatch(executive -> Gender.fromValue(executive.gender()) == Gender.OTHER)) {
            genders.add(Gender.OTHER);
        }
        List<List<Long>> counts = levels.stream().map(level -> genders.stream().map(gender -> recorded.stream()
                .filter(executive -> level.equals(ChartLabels.orNotRecorded(executive.seniority()))
                        && Gender.fromValue(executive.gender()) == gender)
                .count()).toList()).toList();
        List<Boolean> suppressed = counts.stream().map(ProjectChartQueries::tooSmallToShow).toList();
        List<ChartSeries> series = new ArrayList<>();
        for (int column = 0; column < genders.size(); column++) {
            List<Double> values = new ArrayList<>();
            for (int row = 0; row < levels.size(); row++) {
                values.add(suppressed.get(row) ? 0d : (double) counts.get(row).get(column));
            }
            series.add(new ChartSeries(genderLabel(genders.get(column)), values));
        }
        return ChartResult.matrix("Level", levels, series, suppressed, true,
                "A level where any group has fewer than " + SMALLEST_SHOWN_GROUP
                        + " recorded is hidden, so no one can be singled out.");
    }

    private static boolean tooSmallToShow(List<Long> genderCounts) {
        long total = genderCounts.stream().mapToLong(Long::longValue).sum();
        return total < SMALLEST_SHOWN_GROUP
                || genderCounts.stream().anyMatch(count -> count > 0 && count < SMALLEST_SHOWN_GROUP);
    }

    private ChartResult executivesPerResearcher(ChartQueryContext context, Map<String, String> options) {
        TeamPerformanceDto team = reports.readTeam(context.workspaceId(), context.projectId(), null, null);
        return ChartResult.categorical("Researcher", "Executives", "",
                team.researchers().stream().map(researcher -> researcher.name()).toList(),
                team.researchers().stream().map(researcher -> (double) researcher.executives()).toList());
    }

    private ChartResult statusChangesPerWeek(ChartQueryContext context, Map<String, String> options) {
        List<WeeklyCountDto> weekly = report(context).progress().weekly();
        Map<LocalDate, Long> perDay = executives(context).candidates().stream()
                .map(CandidateResponse::statusChangedAt)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(at -> at.atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (WeeklyCountDto week : weekly) {
            long moved = 0;
            for (int day = 0; day < 7; day++) {
                moved += perDay.getOrDefault(week.weekEnding().minusDays(day), 0L);
            }
            labels.add(ChartLabels.WEEK_ENDING.format(week.weekEnding()));
            values.add((double) moved);
        }
        return ChartResult.timeSeries("Week ending", "Executives", labels, values);
    }

    private static ChartResult countBy(String dimension, List<CandidateResponse> executives,
                                       Function<CandidateResponse, String> keyOf, List<String> order) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (order != null) {
            order.forEach(label -> counts.put(label, 0L));
        }
        executives.forEach(executive -> counts.merge(ChartLabels.orNotRecorded(keyOf.apply(executive)), 1L, Long::sum));
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.removeIf(entry -> entry.getValue() == 0);
        if (order == null) {
            entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        }
        return ChartResult.categorical(dimension, "Executives", "", entries.stream().map(Map.Entry::getKey).toList(),
                entries.stream().map(entry -> (double) entry.getValue()).toList());
    }

    private static List<String> levelOrder() {
        List<String> order = new ArrayList<>();
        for (Seniority seniority : Seniority.values()) {
            order.add(seniority.value());
        }
        order.add(ChartLabels.NOT_RECORDED);
        return order;
    }

    private static List<String> presentLevels(List<CandidateResponse> executives) {
        return levelOrder().stream()
                .filter(level -> executives.stream().anyMatch(executive -> level.equals(ChartLabels.orNotRecorded(executive.seniority()))))
                .toList();
    }

    private static List<String> stageOrder(CandidatesResponse found) {
        return found.candidates().stream()
                .map(CandidateResponse::pipelineStage)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt(CandidatePipelineStageDto::position))
                .map(CandidatePipelineStageDto::label)
                .distinct()
                .toList();
    }

    private static String stageLabel(CandidateResponse executive) {
        return executive.pipelineStage() == null ? null : executive.pipelineStage().label();
    }

    private static String sourceLabel(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String spaced = source.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String genderLabel(Gender gender) {
        return switch (gender) {
            case FEMALE -> "Female";
            case MALE -> "Male";
            case OTHER -> "Other";
        };
    }



    private static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
