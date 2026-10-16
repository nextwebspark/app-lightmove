package app.uncava.api.dataexport.service;

import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.core.audit.constant.ProjectEventType;
import app.uncava.api.core.audit.service.AuditService;
import app.uncava.api.core.config.ExportSettings;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.core.security.rbac.ProjectAudience;
import app.uncava.api.customcolumn.dto.CustomColumnDto;
import app.uncava.api.customcolumn.service.CustomColumnService;
import app.uncava.api.dataexport.model.ExportRow;
import app.uncava.api.pairing.model.PairedStage;
import app.uncava.api.pairing.service.StagePairingService;
import app.uncava.api.triagecompany.constant.TriageCompanyStatus;
import app.uncava.api.triagecompany.dto.TriageCompanyResponse;
import app.uncava.api.triagecompany.model.TriageCompanyFilters;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One stage of a mandate's Companies grid as a CSV, paired by {@link StagePairingService}. Not
 * {@code @Transactional}: the seams open their own.
 */
@Service
public class ProjectExportService {

    private final StagePairingService pairing;
    private final CustomColumnService customColumns;
    private final CompaniesCsvWriter writer;
    private final AuditService audit;
    private final ExportSettings caps;

    public ProjectExportService(StagePairingService pairing, CustomColumnService customColumns,
                                CompaniesCsvWriter writer, AuditService audit, UncavaProperties properties) {
        this.pairing = pairing;
        this.customColumns = customColumns;
        this.writer = writer;
        this.audit = audit;
        this.caps = properties.export();
    }

    public String companies(UUID userId, UUID workspaceId, UUID projectId, String statusToken,
                            TriageCompanyFilters filters, ProjectAudience audience,
                            HttpServletRequest httpRequest) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(statusToken);
        PairedStage paired = pairing.pair(workspaceId, projectId, status, filters, caps.maxCompanies(),
                caps.maxCandidates(), audience);
        refuseIfPast("companies", paired.companies().totalCount(), caps.maxCompanies());
        refuseIfPast("executives", paired.totalCandidates(), caps.maxCandidates());

        List<CustomColumnDto> columns = customColumns.list(workspaceId, projectId).columns();
        List<ExportRow> rows = rowsOf(paired);

        audit.projectEvent(ProjectEventType.COMPANIES_EXPORTED, userId, workspaceId, projectId, httpRequest)
                .detail("stage", status.value())
                .detail("rows", String.valueOf(rows.size()))
                .detail("wholeStage", String.valueOf(isUnfiltered(filters)))
                .detail("customColumns", String.valueOf(columns.size()))
                .record();

        return writer.write(columns, rows);
    }

    /** The grid's row model: one line per executive, and a company with none keeps its own line. */
    private static List<ExportRow> rowsOf(PairedStage paired) {
        List<ExportRow> rows = new ArrayList<>();
        for (TriageCompanyResponse company : paired.companies().companies()) {
            List<CandidateResponse> people = paired.peopleAt(company.id());
            if (people.isEmpty()) {
                rows.add(new ExportRow(company, null));
                continue;
            }
            people.forEach(person -> rows.add(new ExportRow(company, person)));
        }
        paired.unassigned().forEach(person -> rows.add(new ExportRow(null, person)));
        return rows;
    }

    private static boolean isUnfiltered(TriageCompanyFilters filters) {
        return blank(filters.companyName()) && blank(filters.executiveName())
                && filters.executiveStatuses().isEmpty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }


    /** Refused rather than truncated: a partial file is indistinguishable from a complete one. */
    private static void refuseIfPast(String what, long total, int cap) {
        if (total > cap) {
            // Both numbers are the server's own, so they may travel in a user-facing message.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This export covers " + total + " " + what + ", past the limit of " + cap
                            + ". Narrow it with the search box, or ask an administrator to raise "
                            + "the export limit.");
        }
    }
}
