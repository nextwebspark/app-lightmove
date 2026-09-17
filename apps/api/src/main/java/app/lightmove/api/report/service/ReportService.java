package app.lightmove.api.report.service;

import app.lightmove.api.report.dto.ReportHeadDto;
import app.lightmove.api.report.dto.ReportResponse;
import app.lightmove.api.report.model.ReportCalendar;
import app.lightmove.api.report.model.ReportSources;
import java.time.Clock;
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
}
