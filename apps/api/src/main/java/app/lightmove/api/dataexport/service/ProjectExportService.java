package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.dataexport.model.ExportRow;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One stage of a mandate's Companies grid, as a CSV.
 *
 * <p>Composes the same three reads the screen makes and pairs them the way the browser does — the
 * stage's companies, the mandate's people, and the mandate's own extra columns. {@code triagecompany}
 * still never learns that people exist.
 *
 * <p>Not {@code @Transactional}: the seams it reads through open and close their own, as
 * {@code TalentMapService} does.
 */
@Service
public class ProjectExportService {

    private final TriageCompanyService triage;
    private final CandidateService candidates;
    private final CustomColumnService customColumns;
    private final CompaniesCsvWriter writer;
    private final AuditService audit;
    private final ExportSettings caps;

    public ProjectExportService(TriageCompanyService triage, CandidateService candidates,
                                CustomColumnService customColumns, CompaniesCsvWriter writer,
                                AuditService audit, LightMoveProperties properties) {
        this.triage = triage;
        this.candidates = candidates;
        this.customColumns = customColumns;
        this.writer = writer;
        this.audit = audit;
        this.caps = properties.export();
    }

    public String companies(UUID userId, UUID workspaceId, UUID projectId, String statusToken,
                            TriageCompanyFilters filters, HttpServletRequest httpRequest) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(statusToken);
        TriageCompaniesResponse stage =
                triage.listAllOfStage(workspaceId, projectId, status, filters, caps.maxCompanies());
        refuseIfPast("companies", stage.totalCount(), caps.maxCompanies());

        CandidatesResponse everyone =
                candidates.listAllOfProject(workspaceId, projectId, caps.maxCandidates());
        refuseIfPast("executives", everyone.totalCount(), caps.maxCandidates());

        List<CustomColumnDto> columns = customColumns.list(workspaceId, projectId).columns();
        List<ExportRow> rows = pair(stage, everyone, status, filters);

        audit.projectEvent(ProjectEventType.COMPANIES_EXPORTED, userId, workspaceId, projectId, httpRequest)
                .detail("stage", status.value())
                .detail("rows", String.valueOf(rows.size()))
                .detail("wholeStage", String.valueOf(isUnfiltered(filters)))
                .detail("customColumns", String.valueOf(columns.size()))
                .record();

        return writer.write(columns, rows);
    }

    /**
     * The grid's own row model: a company with three executives is three lines with the company
     * repeated, and one with none keeps a line of its own — the "Add executive" slot on screen.
     *
     * <p>The two executive filters are applied here as well as in the query above, and for the reason
     * the grid applies them twice too: the server's are company-level (does this company have a
     * matching executive <i>at all</i>), so a kept company would otherwise draw a line for every
     * colleague of the one person who matched.
     *
     * <p>Executives mapped at no company of the mandate are appended, as the grid appends them, on
     * the universe alone. They are dropped while a company-name search is in force, because that
     * search narrows companies by name and a person at no company matches none of it — which is
     * exactly why the screen stops asking for them when the box is filled.
     */
    private static List<ExportRow> pair(TriageCompaniesResponse stage, CandidatesResponse everyone,
                                        TriageCompanyStatus status, TriageCompanyFilters filters) {
        Map<UUID, List<CandidateResponse>> byCompany = new LinkedHashMap<>();
        List<CandidateResponse> unmapped = new ArrayList<>();
        for (CandidateResponse person : everyone.candidates()) {
            if (!matchesExecutiveFilters(person, filters)) {
                continue;
            }
            if (person.triageCompanyId() == null) {
                unmapped.add(person);
            } else {
                byCompany.computeIfAbsent(person.triageCompanyId(), key -> new ArrayList<>()).add(person);
            }
        }

        List<ExportRow> rows = new ArrayList<>();
        for (TriageCompanyResponse company : stage.companies()) {
            List<CandidateResponse> people = byCompany.get(company.id());
            if (people == null || people.isEmpty()) {
                rows.add(new ExportRow(company, null));
                continue;
            }
            people.forEach(person -> rows.add(new ExportRow(company, person)));
        }

        if (status == TriageCompanyStatus.IN_UNIVERSE && blank(filters.companyName())) {
            unmapped.forEach(person -> rows.add(new ExportRow(null, person)));
        }
        return rows;
    }

    /** Whether the file is the stage entire — worth knowing of an export, and not worth the terms. */
    private static boolean isUnfiltered(TriageCompanyFilters filters) {
        return blank(filters.companyName()) && blank(filters.executiveName())
                && filters.executiveStatuses().isEmpty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** The grid's own predicate, on the wire tokens both sides hold. */
    private static boolean matchesExecutiveFilters(CandidateResponse person, TriageCompanyFilters filters) {
        if (!filters.executiveStatuses().isEmpty()
                && !filters.executiveStatuses().contains(person.status())) {
            return false;
        }
        String name = filters.executiveName();
        return blank(name)
                || person.fullName().toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Refused rather than truncated. A file carrying the first five thousand of six thousand rows is
     * indistinguishable from a complete one once it has left the product.
     */
    private static void refuseIfPast(String what, long total, int cap) {
        if (total > cap) {
            // Both numbers are the server's own — a configured ceiling and a count it just made — so
            // they may travel, unlike anything echoed back out of the request.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "This export covers " + total + " " + what + ", past the limit of " + cap
                            + ". Narrow it with the search box, or ask an administrator to raise "
                            + "the export limit.");
        }
    }
}
