package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.dashboard.constant.ChartOptionKey;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final ProjectService projects;
    private final WorkspaceChartData data;
    private final Clock clock;

    List<ChartQuery> queries() {
        DashboardScope here = DashboardScope.WORKSPACE;
        return List.of(
                ChartQuery.kpi(here, "openPositions", "Positions", "Open positions",
                        "Your positions not delivered or closed", this::openPositions),
                ChartQuery.kpi(here, "positionsOffTrack", "Positions", "Positions off track",
                        "Your open positions projected past their target date", this::positionsOffTrack),
                ChartQuery.breakdown(here, "positionsBy", "Positions", "Open positions by …",
                        "Your open positions by health, stage, business unit or lead", ChartType.DONUT,
                        List.of(positionGroups(), ChartOption.top()), this::positionsBy),
                ChartQuery.ranked(here, "longestOpen", "Positions", "Longest open",
                        "Your open positions running the longest, in days since they started", this::longestOpen),
                ChartQuery.overTime(here, "positionsOpenedPerMonth", "Positions", "Positions opened per month",
                        "Your new positions each month, over the last year", ChartType.COLUMN, List.of(),
                        this::positionsOpenedPerMonth),
                ChartQuery.kpi(here, "executivesEngaged", "Pipeline", "Executives engaged",
                        "Executives engaged or further, across your open positions", this::executivesEngaged),
                ChartQuery.breakdown(here, "pipelineByStage", "Pipeline", "Pipeline by stage",
                        "Executives on your open positions, by stage", ChartType.COLUMN, List.of(),
                        this::pipelineByStage),
                ChartQuery.breakdownByTwo(here, "stageByUnit", "Pipeline", "Pipeline stage by business unit",
                        "Each business unit's executives on your open positions, by stage", this::stageByUnit),
                ChartQuery.breakdown(here, "executivesByPosition", "Pipeline", "Executives by position",
                        "Executives mapped on each of your open positions", ChartType.BAR, List.of(ChartOption.top()),
                        this::executivesByPosition),
                ChartQuery.overTime(here, "executivesFiledPerWeek", "Research", "Executives mapped per week",
                        "Across your open positions — not cumulative", ChartType.LINE, List.of(ChartOption.range()),
                        this::executivesFiledPerWeek),
                ChartQuery.breakdown(here, "executivesPerMember", "Research", "Executives found per researcher",
                        "Last 30 days, across your open positions", ChartType.BAR, List.of(ChartOption.top()),
                        this::executivesPerMember));
    }

    private static ChartOption positionGroups() {
        return new ChartOption(ChartOptionKey.GROUP, "Group by", List.of(new ChartChoice("health", "Health"),
                new ChartChoice("stage", "Stage"), new ChartChoice("unit", "Business unit"),
                new ChartChoice("lead", "Lead")), "health");
    }

    /**
     * The open positions the caller may work on. The positions list also carries every mandate a member may only
     * browse, and project content is seat-gated: a chart counting another lead's executives by stage, week or
     * researcher would read a mandate its reader cannot open.
     */
    private List<ProjectResponse> open(ChartQueryContext context) {
        return context.sharedRead("openPositions", () -> projects
                .list(context.userId(), context.workspaceId(), ProjectListScope.LIVE).stream()
                .filter(project -> Boolean.TRUE.equals(project.viewerCanWork()))
                .filter(project -> !project.stage().isDone())
                .toList());
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
            case "stage" -> countBy("Stage", open, project -> ChartLabels.stage(project.stage()));
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
        List<ProjectResponse> all = projects.list(context.userId(), context.workspaceId(), ProjectListScope.LIVE).stream()
                .filter(project -> Boolean.TRUE.equals(project.viewerCanWork()))
                .toList();
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int back = 11; back >= 0; back--) {
            YearMonth month = thisMonth.minusMonths(back);
            labels.add(ChartLabels.MONTH.format(month));
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
            labels.add(ChartLabels.WEEK_ENDING.format(end));
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
        open.forEach(project -> counts.merge(ChartLabels.orNotRecorded(keyOf.apply(project)), 1L, Long::sum));
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

}
