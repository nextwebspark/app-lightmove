package app.lightmove.api.dashboard.service;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.dto.CandidatePipelineStageDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.dashboard.constant.ChartOptionKey;
import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.model.ChartChoice;
import app.lightmove.api.dashboard.model.ChartOption;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.model.ChartQueryRunner;
import app.lightmove.api.dashboard.model.ChartResult;
import app.lightmove.api.dashboard.model.ChartSeries;
import app.lightmove.api.report.dto.DisclosureDto;
import app.lightmove.api.report.dto.MappingProgressDto;
import app.lightmove.api.report.dto.MarketCellDto;
import app.lightmove.api.report.dto.MarketShapeDto;
import app.lightmove.api.report.dto.RemunerationDto;
import app.lightmove.api.report.dto.ReportResponse;
import app.lightmove.api.report.dto.WeeklyCountDto;
import app.lightmove.api.report.service.ReportService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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
    static final String NOT_RECORDED = "Not recorded";
    static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.UK);

    private final ReportService reports;
    private final CandidateService candidates;

    List<ChartQuery> queries() {
        return List.of(
                kpi("companiesMapped", "Mapping progress", "Companies mapped",
                        "Companies with at least one executive, against the scoped universe", this::companiesMapped),
                kpi("executivesIdentified", "Mapping progress", "Executives identified",
                        "Every executive mapped on this position", this::executivesIdentified),
                kpi("daysSinceLastExecutive", "Mapping progress", "Days since the last new executive",
                        "How long since anyone filed a new executive", this::daysSinceLastExecutive),
                series("executivesPerWeek", "Mapping progress", "Executives identified per week",
                        "New executives each week — not cumulative", ChartType.COLUMN, this::executivesPerWeek),
                series("companiesCoveredOverTime", "Mapping progress", "Companies covered over time",
                        "Cumulative companies with an executive, week by week", ChartType.AREA,
                        this::companiesCoveredOverTime),
                new ChartQuery("executivesBy", DashboardScope.PROJECT, "Pipeline", "Executives by …",
                        "The mapped executives by stage, status, level, country or source", ChartShape.CATEGORICAL,
                        false, false, ChartType.BAR, List.of(executiveGroups(), ChartOption.top()),
                        this::executivesBy),
                new ChartQuery("stageByLevel", DashboardScope.PROJECT, "Pipeline", "Stage by level",
                        "Where each level sits in the pipeline", ChartShape.MATRIX, false, false, ChartType.STACKED,
                        List.of(), this::stageByLevel),
                new ChartQuery("companiesBySector", DashboardScope.PROJECT, "Market", "Companies by sector",
                        "The scoped universe by sector", ChartShape.CATEGORICAL, false, false, ChartType.PIE,
                        List.of(ChartOption.top()), this::companiesBySector),
                new ChartQuery("talentHubs", DashboardScope.PROJECT, "Market", "Executives by country",
                        "Where the mapped talent sits, by country", ChartShape.CATEGORICAL, false, false,
                        ChartType.BAR, List.of(ChartOption.top()), this::talentHubs),
                new ChartQuery("sectorByLevel", DashboardScope.PROJECT, "Market", "Sector by level",
                        "Executives in each sector at each level", ChartShape.MATRIX, false, false,
                        ChartType.STACKED, List.of(), this::sectorByLevel),
                new ChartQuery("medianPackageBy", DashboardScope.PROJECT, "Remuneration", "Median package by …",
                        "Disclosed total packages in the brief's currency, median per group — other currencies are "
                                + "not converted",
                        ChartShape.CATEGORICAL, true, true, ChartType.COLUMN,
                        List.of(new ChartOption(ChartOptionKey.GROUP, "Group by",
                                List.of(new ChartChoice("country", "Country"), new ChartChoice("status", "Status")),
                                "country")),
                        this::medianPackageBy),
                kpiStaff("packagesAboveBand", "Remuneration", "Packages above our band",
                        "Disclosed packages priced above the brief's package band", this::packagesAboveBand),
                new ChartQuery("genderByLevel", DashboardScope.PROJECT, "Diversity & DEI", "Gender by level",
                        "Recorded by a researcher only, never inferred — aggregate, no individual identifiable",
                        ChartShape.MATRIX, false, false, ChartType.STACKED, List.of(), this::genderByLevel),
                new ChartQuery("executivesPerResearcher", DashboardScope.PROJECT, "Team",
                        "Executives found per researcher", "Who on the team filed which executives",
                        ChartShape.CATEGORICAL, false, true, ChartType.BAR, List.of(), this::executivesPerResearcher),
                new ChartQuery("statusChangesPerWeek", DashboardScope.PROJECT, "Pipeline",
                        "Executives moved per week", "Executives whose status last changed in each week",
                        ChartShape.TIME_SERIES, false, false, ChartType.LINE, List.of(ChartOption.range()),
                        this::statusChangesPerWeek));
    }

    private static ChartQuery kpi(String id, String category, String label, String description, ChartQueryRunner run) {
        return new ChartQuery(id, DashboardScope.PROJECT, category, label, description, ChartShape.KPI, false, false,
                ChartType.KPI, List.of(), run);
    }

    private static ChartQuery kpiStaff(String id, String category, String label, String description,
                                       ChartQueryRunner run) {
        return new ChartQuery(id, DashboardScope.PROJECT, category, label, description, ChartShape.KPI, false, true,
                ChartType.KPI, List.of(), run);
    }

    private static ChartQuery series(String id, String category, String label, String description, ChartType chart,
                                     ChartQueryRunner run) {
        return new ChartQuery(id, DashboardScope.PROJECT, category, label, description, ChartShape.TIME_SERIES, false,
                false, chart, List.of(ChartOption.range()), run);
    }

    private static ChartOption executiveGroups() {
        return new ChartOption(ChartOptionKey.GROUP, "Group by", List.of(new ChartChoice("stage", "Stage"),
                new ChartChoice("status", "Status"), new ChartChoice("level", "Level"),
                new ChartChoice("country", "Country"), new ChartChoice("source", "Source")), "stage");
    }

    private ReportResponse report(ChartQueryContext context) {
        return reports.read(context.workspaceId(), context.projectId(), ProjectAudience.STAFF);
    }

    private CandidatesResponse executives(ChartQueryContext context) {
        return candidates.listAllOfProject(context.workspaceId(), context.projectId(), EXECUTIVE_CAP,
                ProjectAudience.STAFF);
    }

    private ChartResult companiesMapped(ChartQueryContext context, Map<String, String> options) {
        MappingProgressDto progress = report(context).progress();
        List<Integer> cumulative = progress.companiesCumulative();
        int mapped = cumulative.isEmpty() ? 0 : cumulative.getLast();
        long target = progress.targetCompanies();
        String sub = target == 0 ? "no companies in scope yet" : percent(mapped, target) + "% of the scoped universe";
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
                weekly.stream().map(week -> WEEK_LABEL.format(week.weekEnding())).toList(),
                weekly.stream().map(week -> (double) week.identified()).toList());
    }

    private ChartResult companiesCoveredOverTime(ChartQueryContext context, Map<String, String> options) {
        MappingProgressDto progress = report(context).progress();
        int weeks = Math.min(progress.weekly().size(), progress.companiesCumulative().size());
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int week = 0; week < weeks; week++) {
            labels.add(WEEK_LABEL.format(progress.weekly().get(week).weekEnding()));
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
                                && level.equals(orNotRecorded(executive.seniority())))
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
                .computeIfAbsent(orNotRecorded(groupOf.apply(disclosure)), key -> new ArrayList<>())
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
        String sub = disclosed == 0 ? "no package disclosed yet" : percent(above, disclosed) + "% of disclosed packages";
        return ChartResult.kpi(String.valueOf(above), "/" + disclosed, sub, above > 0);
    }

    /**
     * Counts only a gender a researcher recorded or confirmed: one a model proposed and nobody changed is in
     * {@code aiInferredFields} and is left out. A level with fewer than {@value #SMALLEST_SHOWN_GROUP} recorded is
     * held back and zeroed, so nobody can be singled out of a small group.
     */
    private ChartResult genderByLevel(ChartQueryContext context, Map<String, String> options) {
        CandidatesResponse found = executives(context);
        List<CandidateResponse> recorded = found.candidates().stream()
                .filter(executive -> Gender.fromValue(executive.gender()) != null)
                .filter(executive -> executive.aiInferredFields() == null
                        || !executive.aiInferredFields().contains("gender"))
                .toList();
        List<String> levels = presentLevels(recorded);
        List<Gender> genders = new ArrayList<>(List.of(Gender.FEMALE, Gender.MALE));
        if (recorded.stream().anyMatch(executive -> Gender.fromValue(executive.gender()) == Gender.OTHER)) {
            genders.add(Gender.OTHER);
        }
        List<Boolean> suppressed = levels.stream().map(level -> recorded.stream()
                .filter(executive -> level.equals(orNotRecorded(executive.seniority()))).count()
                < SMALLEST_SHOWN_GROUP).toList();
        List<ChartSeries> series = new ArrayList<>();
        for (Gender gender : genders) {
            List<Double> values = new ArrayList<>();
            for (int index = 0; index < levels.size(); index++) {
                String level = levels.get(index);
                values.add(suppressed.get(index) ? 0d : (double) recorded.stream()
                        .filter(executive -> level.equals(orNotRecorded(executive.seniority()))
                                && Gender.fromValue(executive.gender()) == gender)
                        .count());
            }
            series.add(new ChartSeries(genderLabel(gender), values));
        }
        return ChartResult.matrix("Level", levels, series, suppressed, true,
                "A level with fewer than 3 recorded is hidden so no one can be singled out.");
    }

    private ChartResult executivesPerResearcher(ChartQueryContext context, Map<String, String> options) {
        var team = reports.readTeam(context.workspaceId(), context.projectId(), null, null);
        return ChartResult.categorical("Researcher", "Executives", "",
                team.researchers().stream().map(researcher -> researcher.name()).toList(),
                team.researchers().stream().map(researcher -> (double) researcher.executives()).toList());
    }

    private ChartResult statusChangesPerWeek(ChartQueryContext context, Map<String, String> options) {
        List<WeeklyCountDto> weekly = report(context).progress().weekly();
        CandidatesResponse found = executives(context);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (WeeklyCountDto week : weekly) {
            var end = week.weekEnding();
            var start = end.minusDays(6);
            labels.add(WEEK_LABEL.format(end));
            values.add((double) found.candidates().stream()
                    .filter(executive -> executive.statusChangedAt() != null)
                    .map(executive -> executive.statusChangedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate())
                    .filter(day -> !day.isBefore(start) && !day.isAfter(end))
                    .count());
        }
        return ChartResult.timeSeries("Week ending", "Executives", labels, values);
    }

    private static ChartResult countBy(String dimension, List<CandidateResponse> executives,
                                       Function<CandidateResponse, String> keyOf, List<String> order) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (order != null) {
            order.forEach(label -> counts.put(label, 0L));
        }
        executives.forEach(executive -> counts.merge(orNotRecorded(keyOf.apply(executive)), 1L, Long::sum));
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
        order.add(NOT_RECORDED);
        return order;
    }

    private static List<String> presentLevels(List<CandidateResponse> executives) {
        return levelOrder().stream()
                .filter(level -> executives.stream().anyMatch(executive -> level.equals(orNotRecorded(executive.seniority()))))
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

    static String orNotRecorded(String value) {
        return value == null || value.isBlank() ? NOT_RECORDED : value;
    }

    static long percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 100.0 / whole);
    }

    private static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
