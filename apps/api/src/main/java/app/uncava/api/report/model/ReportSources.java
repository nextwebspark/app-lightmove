package app.uncava.api.report.model;

import app.uncava.api.position.dto.CompensationDto;
import app.uncava.api.project.model.Project;
import app.uncava.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;

/**
 * Everything one report read is computed from, gathered once so the chapters agree. {@code universe}
 * is the companies in universe or shortlisted; the totals are whole even where lists were capped.
 */
public record ReportSources(
        Project project,
        List<TriageCompanyResponse> universe,
        long universeTotal,
        List<ExecutiveRow> executives,
        long executivesTotal,
        CompensationDto compensation
) {

    public boolean isTruncated() {
        return universe.size() < universeTotal || executives.size() < executivesTotal;
    }
}
