package app.lightmove.api.report.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.report.dto.ReportHeadDto;
import app.lightmove.api.report.dto.ReportResponse;
import app.lightmove.api.report.dto.TeamPerformanceDto;
import app.lightmove.api.report.model.ReportCalendar;
import app.lightmove.api.report.model.ReportRange;
import app.lightmove.api.report.model.ReportSources;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * One read of a mandate's report: gather the sources once, then let each chapter draw its own
 * findings from them. Not {@code @Transactional} — the seams it reads through open and close their
 * own, and nothing here writes.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportSourceLoader sources;
    private final MappingProgressReporter progress;
    private final MarketShapeReporter market;
    private final RemunerationReporter remuneration;
    private final DiversityReporter diversity;
    private final TeamSourceLoader team;
    private final TeamPerformanceReporter teamPerformance;
    private final Clock clock;

    public ReportResponse read(UUID workspaceId, UUID projectId) {
        ReportSources gathered = sources.load(workspaceId, projectId);
        ReportCalendar calendar = ReportCalendar.of(gathered.project().getCreatedAt(),
                gathered.project().getTargetDate(), clock);
        ReportHeadDto head = new ReportHeadDto(gathered.universeTotal(), gathered.executivesTotal(),
                gathered.isTruncated(), clock.instant());
        return new ReportResponse(head, progress.report(gathered, calendar), market.report(gathered),
                remuneration.report(gathered), diversity.report(gathered));
    }

    /**
     * Chapter one's researcher breakdown over {@code from}–{@code to}, either end optional and both
     * clamped into the mandate's calendar. Staff-only at the controller; a client seat never reaches it.
     */
    public TeamPerformanceDto readTeam(UUID workspaceId, UUID projectId, LocalDate from, LocalDate to) {
        ReportSources gathered = sources.load(workspaceId, projectId);
        ReportCalendar calendar = ReportCalendar.of(gathered.project().getCreatedAt(),
                gathered.project().getTargetDate(), clock);
        ReportRange range = ReportRange.within(calendar, from, to);
        if (range == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "from", "The start must not be after the end");
        }
        return teamPerformance.report(gathered, calendar, range, team.load(workspaceId, projectId));
    }
}
