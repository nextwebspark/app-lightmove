package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.dashboard.constant.ChartOptionKey;
import app.lightmove.api.dashboard.constant.ChartShape;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.model.ChartChoice;
import app.lightmove.api.dashboard.model.ChartOption;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.model.ChartResult;
import app.lightmove.api.dashboard.model.ChartSeries;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectListScope;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.dto.TeamMemberResponse;
import app.lightmove.api.project.service.ProjectService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The workspace half of the library, across the live positions the caller may browse ({@code ProjectService.list},
 * which narrows a pure client to their seats — though no client reaches a workspace dashboard at all).
 */
@Component
@RequiredArgsConstructor
class WorkspaceChartQueries {

    static final int WEEKS_SHOWN = 12;
    static final int RESEARCH_DAYS = 30;
    static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yy", Locale.UK);

    private final ProjectService projects;
    private final WorkspaceChartData data;
    private final Clock clock;

    List<ChartQuery> queries() {
        return List.of(
                kpi("openPositions", "Positions", "Open positions", "Positions not delivered or closed",
                        this::openPositions),
                kpi("positionsOffTrack", "Positions", "Positions off track",
                        "Open positions projected past their target date", this::positionsOffTrack),
                new ChartQuery("positionsBy", DashboardScope.WORKSPACE, "Positions", "Open positions by …",
                        "Open positions by health, stage, business unit or lead", ChartShape.CATEGORICAL, false, false,
                        ChartType.DONUT, List.of(new ChartOption(ChartOptionKey.GROUP, "Group by",
                                List.of(new ChartChoice("health", "Health"), new ChartChoice("stage", "Stage"),
                                        new ChartChoice("unit", "Business unit"), new ChartChoice("lead", "Lead")),
                                "health"), ChartOption.top()),
                        this::positionsBy),
                new ChartQuery("longestOpen", DashboardScope.WORKSPACE, "Positions", "Longest open",
                        "The open positions running the longest, in days since they started", ChartShape.RANKED,
                        false, false, ChartType.TABLE, List.of(), this::longestOpen),
                new ChartQuery("positionsOpenedPerMonth", DashboardScope.WORKSPACE, "Positions",
                        "Positions opened per month", "New positions each month, over the last year",
                        ChartShape.TIME_SERIES, false, false, ChartType.COLUMN, List.of(), this::positionsOpenedPerMonth),
                kpi("executivesEngaged", "Pipeline", "Executives engaged",
                        "Executives engaged or further, across open positions", this::executivesEngaged),
                new ChartQuery("pipelineByStage", DashboardScope.WORKSPACE, "Pipeline", "Pipeline by stage",
                        "Every open position's executives, by stage", ChartShape.CATEGORICAL, false, false,
                        ChartType.COLUMN, List.of(), this::pipelineByStage),
                new ChartQuery("stageByUnit", DashboardScope.WORKSPACE, "Pipeline", "Pipeline stage by business unit",
                        "Each business unit's executives, by stage", ChartShape.MATRIX, false, false,
                        ChartType.STACKED, List.of(), this::stageByUnit),
                new ChartQuery("executivesByPosition", DashboardScope.WORKSPACE, "Pipeline", "Executives by position",
                        "Executives mapped on each open position", ChartShape.CATEGORICAL, false, false, ChartType.BAR,
                        List.of(ChartOption.top()), this::executivesByPosition),
                new ChartQuery("executivesFiledPerWeek", DashboardScope.WORKSPACE, "Research",
                        "Executives mapped per week", "Across every open position — not cumulative",
                        ChartShape.TIME_SERIES, false, false, ChartType.LINE, List.of(ChartOption.range()),
                        this::executivesFiledPerWeek),
                new ChartQuery("executivesPerMember", DashboardScope.WORKSPACE, "Research",
                        "Executives found per researcher", "Last 30 days, across open positions",
                        ChartShape.CATEGORICAL, false, true, ChartType.BAR, List.of(ChartOption.top()),
                        this::executivesPerMember));
    }

    private static ChartQuery kpi(String id, String category, String label, String description,
                                  app.lightmove.api.dashboard.model.ChartQueryRunner run) {
        return new ChartQuery(id, DashboardScope.WORKSPACE, category, label, description, ChartShape.KPI, false, false,
                ChartType.KPI, List.of(), run);
    }

    private List<ProjectResponse> open(ChartQueryContext context) {
        return projects.list(context.userId(), context.workspaceId(), ProjectListScope.LIVE).stream()
                .filter(project -> !project.stage().isDone())
                .toList();
    }

    private List<UUID> openIds(ChartQueryContext context) {
        return open(context).stream().map(ProjectResponse::id).toList();
    }

    private ChartResult openPositions(ChartQueryContext context, Map<String, String> options) {
        List<ProjectResponse> open = open(context);
        long atRisk = open.stream().filter(project -> project.health() == ProjectHealth.RISK
                || project.health() == ProjectHealth.OFF).count();
        return ChartResult.kpi(String.valueOf(open.size()), "", atRisk + " at risk or off track", false);
    }

    private ChartResult positionsOffTrack(ChartQueryContext context, Map<String, String> options) {
        List<ProjectResponse> open = open(context);
        long off = open.stream().filter(project -> project.health() == ProjectHealth.OFF).count();
        return ChartResult.kpi(String.valueOf(off), "/" + open.size(), "projected past their date", off > 0);
    }

    private ChartResult positionsBy(ChartQueryContext context, Map<String, String> options) {
        List<ProjectResponse> open = open(context);
        return switch (options.get(ChartOptionKey.GROUP.key())) {
            case "stage" -> countBy("Stage", open, project -> titleCase(project.stage().name()));
            case "unit" -> countBy("Business unit", open, ProjectResponse::clientName);
            case "lead" -> countBy("Lead", open, WorkspaceChartQueries::leadOf);
            default -> countBy("Health", open, project -> healthLabel(project.health()));
        };
    }

    private ChartResult longestOpen(ChartQueryContext context, Map<String, String> options) {
        LocalDate today = LocalDate.now(clock);
        List<List<String>> rows = open(context).stream()
                .sorted(Comparator.comparing(project -> startOf(project)))
                .limit(10)
                .map(project -> List.of(project.positionTitle(),
                        String.valueOf(ChronoUnit.DAYS.between(startOf(project), today))))
                .toList();
        return ChartResult.ranked(List.of("Position", "Days open"), rows);
    }

    private ChartResult positionsOpenedPerMonth(ChartQueryContext context, Map<String, String> options) {
        YearMonth thisMonth = YearMonth.now(clock);
        List<ProjectResponse> all = projects.list(context.userId(), context.workspaceId(), ProjectListScope.LIVE);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int back = 11; back >= 0; back--) {
            YearMonth month = thisMonth.minusMonths(back);
            labels.add(MONTH_LABEL.format(month));
            values.add((double) all.stream()
                    .filter(project -> YearMonth.from(project.createdAt().atZone(ZoneOffset.UTC)).equals(month))
                    .count());
        }
        return ChartResult.timeSeries("Month", "Positions", labels, values);
    }

    private ChartResult executivesEngaged(ChartQueryContext context, Map<String, String> options) {
        List<ProjectResponse> open = open(context);
        long engaged = open.stream().mapToLong(ProjectResponse::engagedCandidates).sum();
        long positions = open.stream().filter(project -> project.engagedCandidates() > 0).count();
        return ChartResult.kpi(String.valueOf(engaged), "", "across " + positions
                + (positions == 1 ? " position" : " positions"), false);
    }

    private ChartResult pipelineByStage(ChartQueryContext context, Map<String, String> options) {
        List<WorkspaceChartData.StageCount> stages = data.executivesByStage(context.workspaceId(), openIds(context));
        return ChartResult.categorical("Stage", "Executives", "",
                stages.stream().map(WorkspaceChartData.StageCount::stage).toList(),
                stages.stream().map(stage -> (double) stage.executives()).toList());
    }

    private ChartResult stageByUnit(ChartQueryContext context, Map<String, String> options) {
        List<WorkspaceChartData.UnitStageCount> counts = data.executivesByUnitAndStage(context.workspaceId(),
                openIds(context));
        List<String> units = counts.stream().map(WorkspaceChartData.UnitStageCount::unit).distinct().toList();
        List<String> stages = counts.stream()
                .sorted(Comparator.comparingInt(WorkspaceChartData.UnitStageCount::position))
                .map(WorkspaceChartData.UnitStageCount::stage).distinct().toList();
        List<ChartSeries> series = new ArrayList<>();
        for (String stage : stages) {
            series.add(new ChartSeries(stage, units.stream().map(unit -> (double) counts.stream()
                    .filter(count -> count.unit().equals(unit) && count.stage().equals(stage))
                    .mapToLong(WorkspaceChartData.UnitStageCount::executives).sum()).toList()));
        }
        return ChartResult.matrix("Business unit", units, series, units.stream().map(unit -> false).toList(), false,
                null);
    }

    private ChartResult executivesByPosition(ChartQueryContext context, Map<String, String> options) {
        List<ProjectResponse> open = open(context).stream()
                .sorted(Comparator.comparingLong(ProjectResponse::mappedCandidates).reversed())
                .toList();
        return ChartResult.categorical("Position", "Executives", "",
                open.stream().map(ProjectResponse::positionTitle).toList(),
                open.stream().map(project -> (double) project.mappedCandidates()).toList());
    }

    private ChartResult executivesFiledPerWeek(ChartQueryContext context, Map<String, String> options) {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusWeeks(WEEKS_SHOWN).plusDays(1);
        Map<String, Long> perDay = new LinkedHashMap<>();
        data.executivesFiledPerDay(context.workspaceId(), openIds(context),
                        firstDay.atStartOfDay(ZoneOffset.UTC).toInstant())
                .forEach(day -> perDay.put(day.label(), day.count()));
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int week = WEEKS_SHOWN - 1; week >= 0; week--) {
            LocalDate end = today.minusWeeks(week);
            long sum = 0;
            for (int day = 0; day < 7; day++) {
                sum += perDay.getOrDefault(end.minusDays(day).toString(), 0L);
            }
            labels.add(ProjectChartQueries.WEEK_LABEL.format(end));
            values.add((double) sum);
        }
        return ChartResult.timeSeries("Week ending", "Executives", labels, values);
    }

    private ChartResult executivesPerMember(ChartQueryContext context, Map<String, String> options) {
        List<WorkspaceChartData.LabelledCount> members = data.executivesFiledPerMember(context.workspaceId(),
                openIds(context), clock.instant().minus(Duration.ofDays(RESEARCH_DAYS)));
        return ChartResult.categorical("Researcher", "Executives", "",
                members.stream().map(WorkspaceChartData.LabelledCount::label).toList(),
                members.stream().map(member -> (double) member.count()).toList());
    }

    private static ChartResult countBy(String dimension, List<ProjectResponse> open,
                                       Function<ProjectResponse, String> keyOf) {
        Map<String, Long> counts = new LinkedHashMap<>();
        open.forEach(project -> counts.merge(ProjectChartQueries.orNotRecorded(keyOf.apply(project)), 1L, Long::sum));
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return ChartResult.categorical(dimension, "Positions", "", entries.stream().map(Map.Entry::getKey).toList(),
                entries.stream().map(entry -> (double) entry.getValue()).toList());
    }

    private static String leadOf(ProjectResponse project) {
        return project.team().stream()
                .filter(member -> member.projectRoles() != null && member.projectRoles().contains(ProjectRole.LEAD))
                .map(TeamMemberResponse::fullName)
                .findFirst()
                .orElse(null);
    }

    private static LocalDate startOf(ProjectResponse project) {
        return project.startDate() != null ? project.startDate()
                : project.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static String healthLabel(ProjectHealth health) {
        return switch (health) {
            case OK -> "On track";
            case RISK -> "At risk";
            case OFF -> "Off track";
            case DONE -> "Delivered";
        };
    }

    private static String titleCase(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
