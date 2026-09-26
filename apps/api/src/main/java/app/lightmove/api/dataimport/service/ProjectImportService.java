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
import app.lightmove.api.dataimport.dto.ImportPreviewResponse;
import app.lightmove.api.dataimport.dto.ImportSummaryResponse;
import app.lightmove.api.dataimport.dto.ImportTargetFieldDto;
import app.lightmove.api.dataimport.dto.ProposedColumnMappingDto;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.ImportTally;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.ProposedColumnMappings;
import app.lightmove.api.dataimport.model.SheetColumn;
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
 * Imports a spreadsheet into a mandate's Companies grid.
 *
 * <p><b>This service writes nothing itself.</b> It builds the very requests the Companies drawer
 * posts — a {@link CaptureCompanyRequest}, an {@link EditTriageCompanyRequest}, a
 * {@link SaveCandidateRequest} — and hands them to {@link TriageCompanyService} and
 * {@link CandidateService}, so every scope check, duplicate rule and audit event stays where it
 * already lives.
 *
 * <p>Two calls and no import session between them: the browser still holds the file, so re-posting it
 * with the confirmed mapping costs one parse and saves a staging table and its sweeper.
 *
 * <p><b>A blank cell never clears a stored value.</b> Both update paths replace a row whole, so an
 * update is built from what the row already holds with the file's non-blank cells laid over it.
 *
 * <p><b>Neither method is {@code @Transactional}, and that is the design rather than an omission.</b>
 * A file of a thousand rows will have a bad one in it, and the useful answer is to import the other
 * nine hundred and ninety-nine. Spring marks a transaction rollback-only on <i>any</i> unchecked
 * exception, {@code ApiException} included, so the first refused row poisons the transaction and the
 * commit that follows throws {@code UnexpectedRollbackException} — every row lost. Each call into
 * {@code TriageCompanyService} and {@code CandidateService} therefore runs in its own transaction.
 * Preview stays out of one for a second reason: it calls Vertex, and an open transaction must not
 * wait on a network round trip.
 */
@Service
@RequiredArgsConstructor
public class ProjectImportService {

    private final SpreadsheetReader reader;
    private final ImportTemplateWriter templateWriter;
    private final ColumnMappingProposer proposer;
    private final CustomColumnService customColumns;
    private final TriageCompanyService triage;
    private final CandidateService candidates;
    private final ImportRequestBuilder requests;
    private final ProjectRepository projects;
    private final AuditService audit;

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
                    toDto(proposed.mappings().get(index))));
        }
        return new ImportPreviewResponse(
                FileNameSanitizer.sanitize(file.getOriginalFilename(), "import"),
                sheet.rowCount(),
                columns,
                availableFields(),
                proposed.source().value());
    }

    public ImportSummaryResponse commit(UUID userId, UUID workspaceId, UUID projectId,
                                        MultipartFile file, CommitImportRequest request,
                                        HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        ParsedSheet sheet = reader.read(file);
        ImportTally tally = new ImportTally();

        // Columns first, in their own pass: a value written in the row loop needs its column to exist
        // and to have a field key, and defining one lazily halfway through would leave the rows before
        // it without the column the rows after it got.
        Map<Integer, ResolvedColumn> resolved = resolveColumns(projectId, userId, workspaceId, request, tally);

        for (int rowIndex = 0; rowIndex < sheet.rows().size(); rowIndex++) {
            List<String> row = sheet.rows().get(rowIndex);
            tally.countRow();
            try {
                importRow(userId, workspaceId, projectId, sheet, row, resolved, tally, httpRequest);
            } catch (ApiException | DataAccessException e) {
                // One unusable row must not lose the other nine hundred. The message is the internal
                // detail rather than the user-facing sentence: it names the row's actual problem and
                // reaches only the person who uploaded the file.
                //
                // DataAccessException because a row can lose a version race it did not start:
                // enrichment writes the same company or candidate from its own REQUIRES_NEW
                // transaction, and the optimistic-lock failure that surfaces is not an ApiException.
                // Not RuntimeException — a genuine bug must still fail loudly rather than be filed as
                // one bad row.
                tally.rowFailed(rowIndex + 1, e.getMessage());
            }
        }

        audit.projectEvent(ProjectEventType.SPREADSHEET_IMPORTED, userId, workspaceId, projectId, httpRequest)
                .detail("fileName", FileNameSanitizer.sanitize(file.getOriginalFilename(), "import"))
                .detail("rowsRead", String.valueOf(tally.rowsRead()))
                .detail("companiesCreated", String.valueOf(tally.companiesCreated()))
                .detail("candidatesCreated", String.valueOf(tally.candidatesCreated()))
                .detail("rowsFailed", String.valueOf(tally.failedRows()))
                .record();

        return new ImportSummaryResponse(
                tally.rowsRead(), tally.companiesCreated(), tally.companiesUpdated(),
                tally.companiesSkipped(), tally.candidatesCreated(), tally.candidatesUpdated(),
                tally.customColumnsCreated(), tally.rowErrors());
    }

    /**
     * Turns the confirmed mapping into something the row loop can use, defining any new custom column
     * as it goes.
     *
     * <p>A field claimed by two columns keeps the first. This is the last place that can tell, and the
     * failure it prevents is silent: the second column would overwrite the first on every row and the
     * import would report success.
     */
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
                // "Created" is measured against what the project held when the commit began, so the
                // summary says what changed rather than what was looked up.
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

    /**
     * Defines the custom column one uploaded column asked for, attributing a refusal to that column.
     *
     * <p>This runs before the row loop and outside its per-row catch, so a name clash or the
     * per-project ceiling fails the whole commit. Keyed to the column's index — the one the mapping
     * step renders its rows by — so the refusal points at the row to change rather than being a dead
     * end the Import button repeats.
     */
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

    private void importRow(UUID userId, UUID workspaceId, UUID projectId, ParsedSheet sheet,
                           List<String> row, Map<Integer, ResolvedColumn> resolved, ImportTally tally,
                           HttpServletRequest httpRequest) {
        RowFields fields = RowFields.read(sheet, row, resolved);

        UUID triageCompanyId = null;
        String companyName = fields.field(ImportTargetField.COMPANY_NAME);
        if (companyName != null) {
            TriageCompanyResponse company = upsertCompany(
                    userId, workspaceId, projectId, companyName, fields, tally, httpRequest);
            triageCompanyId = company.id();
        }

        String personName = fields.personName();
        if (personName != null) {
            upsertCandidate(userId, workspaceId, projectId, triageCompanyId, companyName, personName,
                    fields, tally, httpRequest);
        }

        if (companyName == null && personName == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "row carries neither a company name nor a person's name");
        }
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
        // A company carrying a universe id keeps the export's figures, and TriageCompanyService
        // refuses the edit anyway. Its custom columns are still the mandate's to fill, so those go
        // through the edit the row does accept.
        // Keyed on the id rather than on the badge: a plugin capture that resolved against the
        // universe is badged `extension` and carries one all the same.
        if (held.apolloAccountId() == null) {
            TriageCompanyResponse updated = triage.edit(userId, workspaceId, projectId, held.id(),
                    requests.editRequestFor(held, companyName, fields), httpRequest);
            tally.companyUpdated();
            return updated;
        }

        Map<String, String> custom = fields.customValues(CustomColumnTarget.COMPANY);
        if (custom.isEmpty()) {
            tally.companySkipped();
            return held;
        }
        TriageCompanyResponse updated = triage.editCustomFields(
                userId, workspaceId, projectId, held.id(), custom, httpRequest);
        tally.companyUpdated();
        return updated;
    }

    private void upsertCandidate(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                 String companyName, String personName, RowFields fields,
                                 ImportTally tally, HttpServletRequest httpRequest) {
        String email = fields.field(ImportTargetField.CANDIDATE_EMAIL);
        Optional<CandidateResponse> existing =
                candidates.findCandidateOfProject(projectId, triageCompanyId, email, personName);

        if (existing.isEmpty()) {
            candidates.add(userId, workspaceId, projectId,
                    requests.candidateRequestFor(null, triageCompanyId, companyName, personName, fields),
                    httpRequest);
            tally.candidateCreated();
            return;
        }
        CandidateResponse held = existing.get();
        candidates.replace(userId, workspaceId, projectId, held.id(),
                requests.candidateRequestFor(held, triageCompanyId, companyName, personName, fields),
                ContactSource.CSV, httpRequest);
        tally.candidateUpdated();
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
