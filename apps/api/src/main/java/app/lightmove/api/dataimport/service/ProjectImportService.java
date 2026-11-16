package app.lightmove.api.dataimport.service;

import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.FileNameSanitizer;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.dto.CommitImportRequest;
import app.lightmove.api.dataimport.dto.ImportColumnDto;
import app.lightmove.api.dataimport.dto.ImportEnrichmentOfferDto;
import app.lightmove.api.dataimport.dto.ImportEnrichmentQueuedDto;
import app.lightmove.api.dataimport.dto.ImportPreviewResponse;
import app.lightmove.api.dataimport.dto.ImportSummaryResponse;
import app.lightmove.api.dataimport.dto.ImportTargetFieldDto;
import app.lightmove.api.dataimport.dto.ProposedColumnMappingDto;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.ImportTally;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.ProposedColumnMappings;
import app.lightmove.api.dataimport.model.SheetColumn;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentTarget;
import app.lightmove.api.enrichment.importrun.model.QueuedEnrichment;
import app.lightmove.api.enrichment.importrun.service.ImportEnrichmentService;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.EditTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports a spreadsheet into a mandate's Companies grid, writing nothing itself: it builds the drawer's own
 * {@link CaptureCompanyRequest}, {@link EditTriageCompanyRequest} and {@link SaveCandidateRequest} for the services.
 * Not {@code @Transactional}: one refused row would roll back every row; each service call runs in its own.
 */
@Service
@RequiredArgsConstructor
public class ProjectImportService {

    static final String ROW_RACED = "This row was being changed by someone else at the same moment. Import it again.";

    private final SpreadsheetReader reader;
    private final ImportTemplateWriter templateWriter;
    private final ColumnMappingProposer proposer;
    private final CustomColumnService customColumns;
    private final TriageCompanyService triage;
    private final CandidateService candidates;
    private final ImportRequestBuilder requests;
    private final ProjectRepository projects;
    private final AuditService audit;
    private final ImportEnrichmentService enrichment;

    /** The blank CSV a consultant can fill in — carrying this mandate's own custom columns. */
    public String template(UUID workspaceId, UUID projectId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return templateWriter.templateFor(customColumns.list(workspaceId, projectId).columns());
    }

    public ImportPreviewResponse preview(UUID userId, UUID workspaceId, UUID projectId, MultipartFile file) {
        projects.requireInWorkspace(projectId, workspaceId);
        ParsedSheet sheet = reader.read(file);
        List<CustomColumnDto> existing = customColumns.list(workspaceId, projectId).columns();
        ProposedColumnMappings proposed = proposer.propose(userId, sheet, existing);

        List<ImportColumnDto> columns = new ArrayList<>(sheet.columns().size());
        for (int index = 0; index < sheet.columns().size(); index++) {
            SheetColumn column = sheet.columns().get(index);
            columns.add(new ImportColumnDto(
                    column.index(),
                    column.header(),
                    column.valueShape().name().toLowerCase(Locale.ROOT),
                    column.sampleValues(),
                    toDto(proposed.mappings().get(index)),
                    LinkedInLinks.profileSlugsIn(sheet, column.index()).size(),
                    LinkedInLinks.companySlugsIn(sheet, column.index()).size()));
        }
        return new ImportPreviewResponse(
                FileNameSanitizer.sanitize(file.getOriginalFilename(), "import"),
                sheet.rowCount(),
                columns,
                availableFields(),
                proposed.source().value(),
                ImportEnrichmentOfferDto.of(enrichment.offerFor(workspaceId)));
    }

    public ImportSummaryResponse commit(UUID userId, UUID workspaceId, UUID projectId,
                                        MultipartFile file, CommitImportRequest request,
                                        HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        ParsedSheet sheet = reader.read(file);
        ImportTally tally = new ImportTally();
        boolean enrich = request.asksForEnrichment();
        if (enrich) {
            // Before anything is written, custom columns included: a refused enrichment leaves the file importable
            // unchanged with the box unticked.
            enrichment.requireEnrichable(workspaceId, userId,
                    LinkedInLinks.profileSlugsIn(sheet, firstColumnMappedTo(request, ImportTargetField.CANDIDATE_LINKEDIN)).size(),
                    LinkedInLinks.companySlugsIn(sheet, firstColumnMappedTo(request, ImportTargetField.COMPANY_LINKEDIN)).size());
        }
        Set<ImportEnrichmentTarget> targets = new LinkedHashSet<>();

        // Columns first: defined lazily mid-loop, earlier rows would miss a column later rows got.
        Map<Integer, ResolvedColumn> resolved = resolveColumns(projectId, userId, workspaceId, request, tally);

        for (int rowIndex = 0; rowIndex < sheet.rows().size(); rowIndex++) {
            List<String> row = sheet.rows().get(rowIndex);
            tally.countRow();
            try {
                FiledRow filed = importRow(userId, workspaceId, projectId, sheet, row, resolved, tally, httpRequest);
                if (enrich) {
                    targets.addAll(filed.enrichmentTargets());
                }
            } catch (ApiException refused) {
                // The internal detail reaches only the uploader. Not RuntimeException — a real bug must fail loudly.
                tally.rowFailed(rowIndex + 1, refused.getMessage());
            } catch (DataAccessException raced) {
                // A row can lose an optimistic-lock race to enrichment. Never its message: that is SQL and
                // constraint text, which reached the screen.
                tally.rowFailed(rowIndex + 1, ROW_RACED);
            }
        }

        QueuedEnrichment queued = enrich ? enrichment.queue(workspaceId, projectId, userId, targets) : null;

        audit.projectEvent(ProjectEventType.SPREADSHEET_IMPORTED, userId, workspaceId, projectId, httpRequest)
                .detail("fileName", FileNameSanitizer.sanitize(file.getOriginalFilename(), "import"))
                .detail("rowsRead", String.valueOf(tally.rowsRead()))
                .detail("companiesCreated", String.valueOf(tally.companiesCreated()))
                .detail("candidatesCreated", String.valueOf(tally.candidatesCreated()))
                .detail("rowsFailed", String.valueOf(tally.failedRows()))
                .detail("enrich", String.valueOf(enrich))
                .detail("peopleQueued", String.valueOf(queued == null ? 0 : queued.peopleQueued()))
                .detail("companiesQueued", String.valueOf(queued == null ? 0 : queued.companiesQueued()))
                .record();

        return new ImportSummaryResponse(
                tally.rowsRead(), tally.companiesCreated(), tally.companiesUpdated(),
                tally.companiesSkipped(), tally.candidatesCreated(), tally.candidatesUpdated(),
                tally.customColumnsCreated(), tally.rowErrors(),
                queued == null ? null : ImportEnrichmentQueuedDto.of(queued));
    }

    /** The column the server will read a field from: the first to claim it, as {@link #resolveColumns} keeps. */
    private static int firstColumnMappedTo(CommitImportRequest request, ImportTargetField field) {
        return request.columns().stream()
                .filter(mapping -> mapping != null && mapping.index() >= 0 && mapping.targetField() != null)
                .filter(mapping -> field.value().equals(mapping.targetField().trim()))
                .mapToInt(ProposedColumnMappingDto::index)
                .findFirst()
                .orElse(-1);
    }

    /** A field claimed by two columns keeps the first, or the second would silently overwrite it per row. */
    private Map<Integer, ResolvedColumn> resolveColumns(UUID projectId, UUID userId, UUID workspaceId,
                                                        CommitImportRequest request, ImportTally tally) {
        List<CustomColumnDto> existing = customColumns.list(workspaceId, projectId).columns();
        Map<String, CustomColumnDto> byKey = new HashMap<>();
        existing.forEach(column -> byKey.put(column.target() + ":" + column.fieldKey(), column));
        Set<String> heldAtStart = new HashSet<>(byKey.keySet());

        Map<Integer, ResolvedColumn> resolved = new HashMap<>();
        Map<ImportTargetField, Integer> claimedFields = new EnumMap<>(ImportTargetField.class);

        for (ProposedColumnMappingDto mapping : request.columns()) {
            if (mapping == null || mapping.index() < 0) {
                continue;
            }
            ImportTargetField field = mapping.targetField() == null || mapping.targetField().isBlank()
                    ? null
                    : ImportTargetField.fromValue(mapping.targetField().trim());
            if (field != null) {
                if (claimedFields.putIfAbsent(field, mapping.index()) == null) {
                    resolved.put(mapping.index(), new ResolvedColumn.BuiltInColumn(field));
                }
                continue;
            }

            CustomColumnTarget target = customTargetOf(mapping);
            CustomColumnDto column = null;
            if (mapping.customFieldKey() != null && !mapping.customFieldKey().isBlank()) {
                column = byKey.get(target.value() + ":" + mapping.customFieldKey().trim());
            }
            if (column == null && mapping.customLabel() != null && !mapping.customLabel().isBlank()) {
                column = defineColumnFor(mapping, projectId, userId, target);
                // "Created" is against what the project held when the commit began.
                if (heldAtStart.add(column.target() + ":" + column.fieldKey())) {
                    tally.customColumnCreated(column.label());
                }
                byKey.put(column.target() + ":" + column.fieldKey(), column);
            }
            if (column != null) {
                resolved.put(mapping.index(), new ResolvedColumn.DefinedCustomColumn(column));
            }
        }
        return resolved;
    }

    /** A refusal here fails the whole commit, so it names the column's index for the mapping step to point at. */
    private CustomColumnDto defineColumnFor(ProposedColumnMappingDto mapping, UUID projectId,
                                            UUID userId, CustomColumnTarget target) {
        try {
            return customColumns.defineIfAbsent(projectId, userId, target,
                    mapping.customLabel().trim(), customTypeOf(mapping));
        } catch (ApiException e) {
            // The label is request input and stays out of the message; the index is ours to give.
            String message = e.getClientDetail() == null
                    ? e.getCode().defaultMessage()
                    : e.getClientDetail();
            throw ApiException.withField(e.getCode(), "columns[" + mapping.index() + "]", message);
        }
    }

    private FiledRow importRow(UUID userId, UUID workspaceId, UUID projectId, ParsedSheet sheet,
                               List<String> row, Map<Integer, ResolvedColumn> resolved, ImportTally tally,
                               HttpServletRequest httpRequest) {
        RowFields fields = RowFields.read(sheet, row, resolved);
        String companyName = fields.field(ImportTargetField.COMPANY_NAME);
        String personName = fields.personName();
        if (companyName == null && personName == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "row carries neither a company name nor a person's name");
        }

        TriageCompanyResponse company = companyName == null ? null
                : upsertCompany(userId, workspaceId, projectId, companyName, fields, tally, httpRequest);
        UUID triageCompanyId = company == null ? null : company.id();
        CandidateResponse person = personName == null ? null
                : upsertCandidate(userId, workspaceId, projectId, triageCompanyId, companyName, personName,
                        fields, tally, httpRequest);
        return new FiledRow(company, LinkedInLinks.companySlugOf(fields.field(ImportTargetField.COMPANY_LINKEDIN)),
                person, LinkedInLinks.profileSlugOf(fields.field(ImportTargetField.CANDIDATE_LINKEDIN)));
    }

    private TriageCompanyResponse upsertCompany(UUID userId, UUID workspaceId, UUID projectId,
                                                String companyName, RowFields fields, ImportTally tally,
                                                HttpServletRequest httpRequest) {
        Optional<TriageCompanyResponse> existing =
                triage.findCompanyOfProjectByName(projectId, companyName);
        if (existing.isEmpty()) {
            TriageCompanyResponse created = triage.capture(userId, workspaceId, projectId,
                    requests.captureRequestFor(companyName, fields), httpRequest);
            tally.companyCreated();
            return created;
        }

        TriageCompanyResponse held = existing.get();
        // A held company's note is filed like a new one's; the edit request has no note to carry it.
        boolean noted = triage.fileNoteFromDoor(userId, workspaceId, projectId, held.id(),
                requests.companyNoteOf(fields), httpRequest);
        // Keyed on the id, not the badge: a market company takes only its custom columns.
        if (held.apolloAccountId() == null) {
            TriageCompanyResponse updated = triage.edit(userId, workspaceId, projectId, held.id(),
                    requests.editRequestFor(held, companyName, fields), httpRequest);
            tally.companyUpdated();
            return updated;
        }

        Map<String, String> custom = fields.customValues(CustomColumnTarget.COMPANY);
        if (custom.isEmpty()) {
            if (noted) {
                tally.companyUpdated();
            } else {
                tally.companySkipped();
            }
            return held;
        }
        TriageCompanyResponse updated = triage.editCustomFields(
                userId, workspaceId, projectId, held.id(), custom, httpRequest);
        tally.companyUpdated();
        return updated;
    }

    private CandidateResponse upsertCandidate(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                              String companyName, String personName, RowFields fields,
                                              ImportTally tally, HttpServletRequest httpRequest) {
        Optional<CandidateResponse> existing = candidates.findCandidateOfProject(projectId, triageCompanyId,
                RowValues.linkedinProfile(fields.field(ImportTargetField.CANDIDATE_LINKEDIN)),
                RowValues.email(fields.field(ImportTargetField.CANDIDATE_EMAIL)), personName);

        if (existing.isEmpty()) {
            CandidateResponse created = candidates.add(userId, workspaceId, projectId,
                    requests.candidateRequestFor(null, triageCompanyId, companyName, personName, fields),
                    httpRequest);
            tally.candidateCreated();
            return created;
        }
        CandidateResponse held = existing.get();
        CandidateResponse updated = candidates.replace(userId, workspaceId, projectId, held.id(),
                requests.candidateRequestFor(held, triageCompanyId, companyName, personName, fields),
                ContactSource.CSV, httpRequest);
        tally.candidateUpdated();
        return updated;
    }

    private static CustomColumnTarget customTargetOf(ProposedColumnMappingDto mapping) {
        CustomColumnTarget target = mapping.customTarget() == null
                ? null
                : CustomColumnTarget.fromValue(mapping.customTarget().trim().toLowerCase(Locale.ROOT));
        return target == null ? CustomColumnTarget.CANDIDATE : target;
    }

    private static CustomColumnType customTypeOf(ProposedColumnMappingDto mapping) {
        CustomColumnType type = mapping.customType() == null
                ? null
                : CustomColumnType.fromValue(mapping.customType().trim().toLowerCase(Locale.ROOT));
        return type == null ? CustomColumnType.TEXT : type;
    }

    private static List<ImportTargetFieldDto> availableFields() {
        return Arrays.stream(ImportTargetField.values())
                .map(field -> new ImportTargetFieldDto(
                        field.value(), field.label(), field.target().value()))
                .toList();
    }

    private static ProposedColumnMappingDto toDto(ColumnMapping mapping) {
        return new ProposedColumnMappingDto(
                mapping.columnIndex(),
                mapping.header(),
                mapping.field() == null ? null : mapping.field().value(),
                mapping.customFieldKey(),
                mapping.customLabel(),
                mapping.customColumnTarget() == null ? null : mapping.customColumnTarget().value(),
                mapping.customType() == null ? null : mapping.customType().value());
    }
}
