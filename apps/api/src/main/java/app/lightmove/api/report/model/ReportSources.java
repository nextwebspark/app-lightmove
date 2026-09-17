package app.lightmove.api.report.model;

import app.lightmove.api.position.dto.CompensationDto;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;

/**
 * Everything one read of the report is computed from, gathered once so the four chapters agree with
 * each other. {@code universe} is the mandate's companies still in play — in universe or shortlisted,
 * never declined — and the two totals are the whole even where the lists were capped.
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
