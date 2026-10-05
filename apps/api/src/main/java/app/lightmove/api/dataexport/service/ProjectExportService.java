package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.dataexport.model.ExportRow;
import app.lightmove.api.pairing.model.PairedStage;
import app.lightmove.api.pairing.service.StagePairingService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
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
                                CompaniesCsvWriter writer, AuditService audit, LightMoveProperties properties) {
        this.pairing = pairing;
        this.customColumns = customColumns;
        this.writer = writer;
        this.audit = audit;
        this.caps = properties.export();
    }

    public String companies(UUID userId, UUID workspaceId, UUID projectId, String statusToken,
                            TriageCompanyFilters filters, HttpServletRequest httpRequest) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(statusToken);
        PairedStage paired = pairing.pair(workspaceId, projectId, status, filters, caps.maxCompanies(),
                caps.maxCandidates());
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
