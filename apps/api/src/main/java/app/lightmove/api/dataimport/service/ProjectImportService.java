package app.lightmove.api.dataimport.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports a spreadsheet into a mandate's Companies grid.
 *
 * <p><b>This service writes nothing itself.</b> It reads the file, works out what each column means,
 * and then builds the very requests the Companies drawer posts — a {@link CaptureCompanyRequest}, an
 * {@link EditTriageCompanyRequest}, a {@link SaveCandidateRequest} — and hands them to
 * {@link TriageCompanyService} and {@link CandidateService}. Every scope check, duplicate rule, source
 * resolution, snapshot and audit event therefore stays in the one place that already owns it, and an
 * import cannot drift from what the screen does.
 *
 * <p>Two calls, and no import session between them. {@link #preview} reads the file and proposes a
 * mapping; {@link #commit} takes the same file back with the mapping a person confirmed. The browser
 * still holds the file, so re-posting it costs one parse and saves a staging table, an expiry policy
 * and a sweeper for the imports nobody came back to finish.
 *
 * <p><b>A blank cell never clears a stored value.</b> Both update paths replace a row whole, so an
 * update is built from what the row already holds with the file's non-blank cells laid over it. A
 * spreadsheet that carries only names and emails must not empty out the headcounts a researcher
 * entered by hand.
 *
 * <p><b>Neither method is {@code @Transactional}, and that is the design rather than an omission.</b>
 * A file of a thousand rows will have a bad one in it, and the useful answer is to import the other
 * nine hundred and ninety-nine and say which one failed. One transaction around the whole commit
 * cannot do that: Spring marks a transaction rollback-only on <i>any</i> unchecked exception,
 * {@code ApiException} included, so the first refused row poisons the transaction and the commit that
 * follows throws {@code UnexpectedRollbackException} — every row lost, and the caller told nothing
 * useful about why. Each call into {@code TriageCompanyService} and {@code CandidateService} therefore
 * runs in its own transaction, which is the granularity the row loop actually needs. The accepted
 * consequence is that a row whose company is written and whose person is then refused leaves the
 * company behind; that company is real data the file carried, and the row error names what was
 * missed. Preview stays out of a transaction for a second reason: it calls Vertex, and an open
 * transaction must not wait on a network round trip.
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
    private final ProjectRepository projects;
    private final AuditService audit;

    /** The blank CSV a consultant can fill in — carrying this mandate's own custom columns. */
    public String template(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        return templateWriter.templateFor(customColumns.list(workspaceId, projectId).columns());
    }

    public ImportPreviewResponse preview(UUID workspaceId, UUID projectId, MultipartFile file) {
        requireProject(projectId, workspaceId);
        ParsedSheet sheet = reader.read(file);
        List<CustomColumnDto> existing = customColumns.list(workspaceId, projectId).columns();
        ProposedColumnMappings proposed = proposer.propose(sheet, existing);

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
                safeFileNameOf(file.getOriginalFilename()),
                sheet.rowCount(),
                columns,
                availableFields(),
                proposed.source().value());
    }

    public ImportSummaryResponse commit(UUID userId, UUID workspaceId, UUID projectId,
                                        MultipartFile file, CommitImportRequest request,
                                        HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
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
            } catch (ApiException e) {
                // One unusable row must not lose the other nine hundred. The message is the internal
                // detail rather than the user-facing sentence: it names the row's actual problem, and
                // it reaches only the person who uploaded the file they are being told about.
                tally.rowFailed(rowIndex + 1, e.getMessage());
            }
        }

        audit.event(ProjectEventType.SPREADSHEET_IMPORTED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("fileName", safeFileNameOf(file.getOriginalFilename()))
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
     * <p>A field claimed by two columns keeps the first. The mapping step should not produce one, but
     * this is the last place that can tell — and the failure it prevents is silent: the second column
     * would simply overwrite the first on every row, and the import would report success.
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
                    resolved.put(mapping.index(), ResolvedColumn.onto(field));
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
                // "Created" is measured against what the project held when the commit began, so a
                // column two headers both map to is reported once, and one that already existed is
                // not reported at all — the summary says what changed, not what was looked up.
                if (heldAtStart.add(column.target() + ":" + column.fieldKey())) {
                    tally.customColumnCreated(column.label());
                }
                byKey.put(column.target() + ":" + column.fieldKey(), column);
            }
            if (column != null) {
                resolved.put(mapping.index(), ResolvedColumn.into(column));
            }
        }
        return resolved;
    }

    /**
     * Defines the custom column one uploaded column asked for, attributing a refusal to that column.
     *
     * <p>This runs before the row loop and outside its per-row catch, so a name clash or the
     * per-project ceiling fails the whole commit. Left unattributed it was a dead end: the mapping
     * step named no column and the Import button would fail identically however many times it was
     * pressed. Keyed to the column's index, the same one the mapping step renders its rows by, the
     * refusal points at the row to change.
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
                    captureRequestFor(companyName, fields), httpRequest);
            tally.companyCreated();
            return created;
        }

        TriageCompanyResponse held = existing.get();
        // A company taken out of the Apollo universe keeps the export's own figures — that is what the
        // Source badge on the grid promises, and TriageCompanyService refuses the edit anyway. Its
        // custom columns are still the mandate's to fill, so those go through the edit the row does
        // accept: a note. Everything else about the row is left exactly as the market published it.
        if (!"strategy".equals(held.source())) {
            TriageCompanyResponse updated = triage.edit(userId, workspaceId, projectId, held.id(),
                    editRequestFor(held, companyName, fields), httpRequest);
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
                    candidateRequestFor(null, triageCompanyId, companyName, personName, fields),
                    httpRequest);
            tally.candidateCreated();
            return;
        }
        CandidateResponse held = existing.get();
        candidates.replace(userId, workspaceId, projectId, held.id(),
                candidateRequestFor(held, triageCompanyId, companyName, personName, fields), httpRequest);
        tally.candidateUpdated();
    }

    private CaptureCompanyRequest captureRequestFor(String companyName, RowFields fields) {
        return new CaptureCompanyRequest(
                RowValues.text(companyName, 200),
                // The import is its own door, recorded so the grid's Source badge can say a figure
                // came out of somebody's spreadsheet rather than out of the market.
                "csv",
                "inUniverse",
                RowValues.text(fields.field(ImportTargetField.COMPANY_INDUSTRY), 200),
                RowValues.text(fields.field(ImportTargetField.COMPANY_COUNTRY), 100),
                RowValues.text(fields.field(ImportTargetField.COMPANY_CITY), 100),
                RowValues.integer(fields.field(ImportTargetField.COMPANY_EMPLOYEES)),
                RowValues.number(fields.field(ImportTargetField.COMPANY_REVENUE)),
                foundedYearOf(fields),
                RowValues.text(fields.field(ImportTargetField.COMPANY_WEBSITE), 500),
                RowValues.text(fields.field(ImportTargetField.COMPANY_LINKEDIN), 500),
                RowValues.text(fields.field(ImportTargetField.COMPANY_DESCRIPTION), 2000),
                null,
                RowValues.text(fields.field(ImportTargetField.COMPANY_NOTE), 2000),
                fields.customValues(CustomColumnTarget.COMPANY));
    }

    /**
     * The stored row with the file's non-blank cells laid over it — an edit replaces a company whole,
     * so anything the file does not carry has to be restated or it is lost.
     */
    private EditTriageCompanyRequest editRequestFor(TriageCompanyResponse held, String companyName,
                                                    RowFields fields) {
        return new EditTriageCompanyRequest(
                firstOf(RowValues.text(companyName, 200), held.companyName()),
                firstOf(RowValues.text(fields.field(ImportTargetField.COMPANY_INDUSTRY), 200), held.industry()),
                firstOf(RowValues.text(fields.field(ImportTargetField.COMPANY_COUNTRY), 100), held.companyCountry()),
                firstOf(RowValues.text(fields.field(ImportTargetField.COMPANY_CITY), 100), held.companyCity()),
                firstOf(RowValues.integer(fields.field(ImportTargetField.COMPANY_EMPLOYEES)), held.numEmployees()),
                firstOf(RowValues.number(fields.field(ImportTargetField.COMPANY_REVENUE)), held.annualRevenue()),
                firstOf(foundedYearOf(fields), held.foundedYear()),
                firstOf(RowValues.text(fields.field(ImportTargetField.COMPANY_WEBSITE), 500), held.website()),
                firstOf(RowValues.text(fields.field(ImportTargetField.COMPANY_LINKEDIN), 500), held.companyLinkedinUrl()),
                firstOf(RowValues.text(fields.field(ImportTargetField.COMPANY_DESCRIPTION), 2000), held.shortDescription()),
                fields.customValues(CustomColumnTarget.COMPANY));
    }

    /**
     * The same overlay for a person. {@code held} is null when creating, in which case every stored
     * value is simply absent and the file's own cells stand alone.
     */
    private SaveCandidateRequest candidateRequestFor(CandidateResponse held, UUID triageCompanyId,
                                                     String companyName, String personName,
                                                     RowFields fields) {
        return new SaveCandidateRequest(
                triageCompanyId,
                firstOf(RowValues.text(personName, 200), held == null ? null : held.fullName()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_TITLE), 200),
                        held == null ? null : held.title()),
                firstOf(RowValues.seniority(fields.field(ImportTargetField.CANDIDATE_SENIORITY)),
                        held == null ? null : held.seniority()),
                // Never from the file: a "status" column in somebody's spreadsheet is their pipeline,
                // and overwriting this mandate's own decision with it would undo a researcher's work.
                held == null ? null : held.status(),
                firstOf(RowValues.text(companyName, 200), held == null ? null : held.companyName()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_EMAIL), 320),
                        held == null ? null : held.email()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_PHONE), 50),
                        held == null ? null : held.phone()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_LINKEDIN), 500),
                        held == null ? null : held.linkedinUrl()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_COUNTRY), 100),
                        held == null ? null : held.locationCountry()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_CITY), 100),
                        held == null ? null : held.locationCity()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_NATIONALITY), 100),
                        held == null ? null : held.nationality()),
                firstOf(yearsExperienceOf(fields), held == null ? null : held.yearsExperience()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_SUMMARY), 4000),
                        held == null ? null : held.summary()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_NOTE), 2000),
                        held == null ? null : held.note()),
                compensationFor(held, fields),
                held == null ? null : held.career(),
                held == null ? null : held.languages(),
                held == null ? "csv" : held.source(),
                held == null ? null : held.sourceUrl(),
                fields.customValues(CustomColumnTarget.CANDIDATE));
    }

    private app.lightmove.api.candidate.dto.CandidateCompensationDto compensationFor(
            CandidateResponse held, RowFields fields) {
        var stored = held == null ? null : held.compensation();
        return new app.lightmove.api.candidate.dto.CandidateCompensationDto(
                firstOf(RowValues.currency(fields.field(ImportTargetField.CANDIDATE_CURRENCY)),
                        stored == null ? null : stored.currency()),
                firstOf(RowValues.number(fields.field(ImportTargetField.CANDIDATE_BASE_SALARY)),
                        stored == null ? null : stored.baseSalary()),
                firstOf(RowValues.number(fields.field(ImportTargetField.CANDIDATE_BONUS)),
                        stored == null ? null : stored.bonus()),
                firstOf(RowValues.number(fields.field(ImportTargetField.CANDIDATE_ALLOWANCES)),
                        stored == null ? null : stored.allowances()),
                firstOf(RowValues.number(fields.field(ImportTargetField.CANDIDATE_LONG_TERM_INCENTIVE)),
                        stored == null ? null : stored.longTermIncentive()),
                firstOf(RowValues.text(fields.field(ImportTargetField.CANDIDATE_NOTICE_PERIOD), 100),
                        stored == null ? null : stored.noticePeriod()));
    }

    /** Refused rather than truncated by the DTO's own {@code @Max}: a bad year is not a year. */
    private static Integer foundedYearOf(RowFields fields) {
        Integer year = RowValues.integer(fields.field(ImportTargetField.COMPANY_FOUNDED));
        return year == null || year < 1800 || year > 2100 ? null : year;
    }

    private static Integer yearsExperienceOf(RowFields fields) {
        Integer years = RowValues.integer(fields.field(ImportTargetField.CANDIDATE_YEARS_EXPERIENCE));
        return years == null || years < 0 || years > 70 ? null : years;
    }

    private static <T> T firstOf(T fromFile, T stored) {
        return fromFile != null ? fromFile : stored;
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

    /**
     * The filename is caller-supplied and reaches an audit detail and the preview response, so the
     * path separators and control characters that would let it forge either are stripped here — the
     * same guard {@code PositionDocumentService} applies for the same reason.
     */
    private static String safeFileNameOf(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "import";
        }
        String withoutPath = originalFileName.replaceAll(".*[/\\\\]", "");
        String cleaned = withoutPath.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (cleaned.isEmpty()) {
            return "import";
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** What one sheet column turned out to be: a built-in field, or a custom column of the mandate's. */
    private record ResolvedColumn(ImportTargetField field, CustomColumnDto customColumn) {

        static ResolvedColumn onto(ImportTargetField field) {
            return new ResolvedColumn(field, null);
        }

        static ResolvedColumn into(CustomColumnDto customColumn) {
            return new ResolvedColumn(null, customColumn);
        }
    }

    /**
     * One row's cells, indexed by what they mean rather than by where they sit — so the writers above
     * read as the fields they build rather than as arithmetic on column positions.
     */
    private record RowFields(Map<ImportTargetField, String> byField,
                             Map<CustomColumnTarget, Map<String, String>> customByTarget) {

        static RowFields read(ParsedSheet sheet, List<String> row, Map<Integer, ResolvedColumn> resolved) {
            Map<ImportTargetField, String> byField = new EnumMap<>(ImportTargetField.class);
            Map<CustomColumnTarget, Map<String, String>> custom = new EnumMap<>(CustomColumnTarget.class);
            custom.put(CustomColumnTarget.COMPANY, new HashMap<>());
            custom.put(CustomColumnTarget.CANDIDATE, new HashMap<>());

            resolved.forEach((columnIndex, column) -> {
                String value = sheet.cell(row, columnIndex);
                if (value == null) {
                    return;
                }
                if (column.field() != null) {
                    byField.put(column.field(), value);
                } else {
                    CustomColumnDto defined = column.customColumn();
                    custom.get(CustomColumnTarget.fromValue(defined.target()))
                            .put(defined.fieldKey(), value);
                }
            });
            return new RowFields(byField, custom);
        }

        String field(ImportTargetField field) {
            return byField.get(field);
        }

        Map<String, String> customValues(CustomColumnTarget target) {
            return Map.copyOf(customByTarget.get(target));
        }

        /**
         * The person's name, joined from first and last when the file splits them — which most
         * LinkedIn and ATS exports do, and which would otherwise import as no person at all.
         */
        String personName() {
            String full = byField.get(ImportTargetField.CANDIDATE_NAME);
            if (full != null) {
                return full;
            }
            String first = byField.get(ImportTargetField.CANDIDATE_FIRST_NAME);
            String last = byField.get(ImportTargetField.CANDIDATE_LAST_NAME);
            if (first == null && last == null) {
                return null;
            }
            return (first == null ? "" : first + " ") .concat(last == null ? "" : last).trim();
        }
    }
}
