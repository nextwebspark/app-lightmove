package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.llm.config.ChatClientConfig;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.HeuristicProposal;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.constant.MappingSource;
import app.lightmove.api.dataimport.model.ProposedColumnMappings;
import app.lightmove.api.dataimport.model.ProposedMapping;
import app.lightmove.api.dataimport.model.SheetColumn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the model which field each column of an uploaded sheet means.
 *
 * <p>Built on the {@link ChatClient} bean the application already has: the model, its temperature and
 * its advisors are set once in {@code ChatClientConfig} and are not restated here. All this adds is a
 * system prompt of its own, set per call so that bean stays reusable, and a structured answer.
 *
 * <p><b>No cell values are sent.</b> A spreadsheet of executives is client and candidate PII — the
 * same PII that was deliberately kept out of the application log — so the request carries each
 * column's header and a locally computed shape ({@code looks like an email}, {@code looks numeric})
 * and nothing else. That is nearly all the signal a mapping needs, and it means an import of a
 * confidential longlist does not become an upload of one.
 * {@code lightmove.spreadsheet-import.send-sample-values} exists so an operator can make the opposite
 * trade knowingly rather than by editing code.
 *
 * <p><b>The answer is checked, never trusted.</b> Every entry is matched back to a real header by
 * name, unknown tokens are dropped, and a field claimed twice goes to the first column that claimed
 * it — a model that maps two headers onto {@code candidateEmail} would otherwise have the second
 * silently overwrite the first. Anything the model did not answer for falls back to
 * {@link HeuristicColumnMatcher}, as does the whole sheet when the call fails: Vertex needs
 * Application Default Credentials on every path including a plain local run, and an import that was
 * impossible without them would be an import most people never got to use.
 */
@Service
@Slf4j
public class ColumnMappingProposer {

    /** Names this feature in the shared client's log line. */
    private static final String PROMPT_ID = "import-column-mapping";

    /** Enough of a sheet to describe its columns; a header list is short, and this bounds a wide file. */
    private static final int MAX_HEADERS_SENT = 120;

    private final ChatClient chatClient;
    private final HeuristicColumnMatcher heuristics;
    private final Resource systemPrompt;
    private final SpreadsheetImportSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, and this also derives the settings branch from the properties root.
    public ColumnMappingProposer(ChatClient chatClient,
                                 HeuristicColumnMatcher heuristics,
                                 @Value("classpath:prompts/import-column-mapping-system.st") Resource systemPrompt,
                                 LightMoveProperties properties) {
        this.chatClient = chatClient;
        this.heuristics = heuristics;
        this.systemPrompt = systemPrompt;
        this.settings = properties.spreadsheetImport();
    }

    public ProposedColumnMappings propose(ParsedSheet sheet, List<CustomColumnDto> existingColumns) {
        HeuristicProposal heuristic = heuristics.propose(sheet, existingColumns);

        // The model is asked only where there is genuine doubt. A file whose every header is a known
        // spelling — anything built from the downloadable template, and most second imports — is
        // already mapped, and paying Vertex to confirm it would be paying for nothing.
        if (heuristic.everyColumnCertain()) {
            return new ProposedColumnMappings(heuristic.mappings(), MappingSource.EXACT_HEADERS);
        }

        List<ColumnMapping> fallback = heuristic.mappings();
        try {
            ProposedMapping answered = ask(sheet, existingColumns);
            if (answered == null) {
                return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
            }
            return new ProposedColumnMappings(
                    reconcile(sheet, existingColumns, answered, fallback), MappingSource.MODEL);
        } catch (RuntimeException e) {
            // Deliberately broad and deliberately quiet: every way this call can fail — no
            // credentials, no quota, a network that cannot reach Vertex, an answer that will not bind
            // — has the same right answer, which is the mapping the heuristic already worked out. The
            // user still gets a mapping step to correct, and the response says which of the three
            // produced it rather than claiming the model did.
            log.warn("Column mapping fell back to the heuristic matcher: {}", e.toString());
            return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
        }
    }

    private ProposedMapping ask(ParsedSheet sheet, List<CustomColumnDto> existingColumns) {
        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatClientConfig.PROMPT_ID, PROMPT_ID))
                .system(systemPrompt)
                .user(user -> user.text("""
                        Columns in the uploaded file:
                        {columns}

                        Fields available to map onto:
                        {fields}

                        Custom columns this project already has:
                        {existing}
                        """)
                        .param("columns", describeColumns(sheet))
                        .param("fields", describeFields())
                        .param("existing", describeExisting(existingColumns)))
                .call()
                .entity(ProposedMapping.class);
    }

    private String describeColumns(ParsedSheet sheet) {
        return sheet.columns().stream()
                .limit(MAX_HEADERS_SENT)
                .map(this::describeColumn)
                .collect(Collectors.joining("\n"));
    }

    private String describeColumn(SheetColumn column) {
        StringBuilder described = new StringBuilder()
                .append("- \"").append(column.header()).append("\"")
                .append(" (values look like: ").append(shapeLabel(column)).append(")");
        if (settings.sendSampleValues() && !column.sampleValues().isEmpty()) {
            described.append(" e.g. ").append(String.join(" | ", column.sampleValues()));
        }
        return described.toString();
    }

    private static String shapeLabel(SheetColumn column) {
        if (column.allBlank()) {
            return "the column is empty";
        }
        return switch (column.valueShape()) {
            case EMAIL -> "email addresses";
            case URL -> "web addresses";
            case NUMBER -> "numbers";
            case DATE -> "dates";
            case BOOLEAN -> "yes/no values";
            case SHORT_TEXT -> "short text";
            case LONG_TEXT -> "long text";
            case BLANK -> "the column is empty";
        };
    }

    private static String describeFields() {
        return java.util.Arrays.stream(ImportTargetField.values())
                .map(field -> "- %s (%s, %s)".formatted(
                        field.value(), field.label(), field.target().value()))
                .collect(Collectors.joining("\n"));
    }

    private static String describeExisting(List<CustomColumnDto> existingColumns) {
        if (existingColumns.isEmpty()) {
            return "- none";
        }
        return existingColumns.stream()
                .map(column -> "- \"%s\" (%s, %s)".formatted(
                        column.label(), column.target(), column.dataType()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Turns the model's answer into mappings for the sheet's real columns, keeping the heuristic's
     * verdict wherever the answer cannot be used.
     *
     * <p>Matching is by header rather than by position, because a model that drops, reorders or
     * invents an entry would otherwise shift every mapping after it onto the wrong column — the one
     * failure mode where a plausible-looking answer writes a whole file into the wrong fields.
     */
    private List<ColumnMapping> reconcile(ParsedSheet sheet, List<CustomColumnDto> existingColumns,
                                          ProposedMapping answered, List<ColumnMapping> fallback) {
        Map<String, ProposedMapping.ProposedColumn> byHeader = new HashMap<>();
        if (answered.columns() != null) {
            for (ProposedMapping.ProposedColumn column : answered.columns()) {
                if (column != null && column.header() != null) {
                    byHeader.putIfAbsent(normalisedHeader(column.header()), column);
                }
            }
        }

        Set<ImportTargetField> claimed = new LinkedHashSet<>();
        List<ColumnMapping> mappings = new ArrayList<>(sheet.columns().size());
        for (int index = 0; index < sheet.columns().size(); index++) {
            SheetColumn column = sheet.columns().get(index);
            ProposedMapping.ProposedColumn answer = byHeader.get(normalisedHeader(column.header()));
            mappings.add(resolve(column, answer, existingColumns, fallback.get(index), claimed));
        }
        return mappings;
    }

    private ColumnMapping resolve(SheetColumn column, ProposedMapping.ProposedColumn answer,
                                  List<CustomColumnDto> existingColumns, ColumnMapping fallback,
                                  Set<ImportTargetField> claimed) {
        if (answer == null) {
            return keepIfUnclaimed(fallback, claimed);
        }

        ImportTargetField field = answer.targetField() == null
                ? null
                : ImportTargetField.fromValue(answer.targetField().trim());
        if (field != null) {
            // A field two columns both claim goes to the first; the loser keeps its data as a custom
            // column rather than silently overwriting what the winner wrote.
            if (claimed.add(field)) {
                return ColumnMapping.onto(column.index(), column.header(), field);
            }
            return customColumnFor(column, answer, existingColumns);
        }

        if (answer.customLabel() != null && !answer.customLabel().isBlank()) {
            return customColumnFor(column, answer, existingColumns);
        }

        // The model said the column carries nothing worth importing. An empty column is an easy
        // agreement; a column with values in it is not the model's call to discard, so the heuristic's
        // custom column stands and the user decides in the mapping step.
        return column.allBlank()
                ? ColumnMapping.ignored(column.index(), column.header())
                : keepIfUnclaimed(fallback, claimed);
    }

    private ColumnMapping keepIfUnclaimed(ColumnMapping fallback, Set<ImportTargetField> claimed) {
        if (fallback.field() != null && !claimed.add(fallback.field())) {
            return ColumnMapping.ignored(fallback.columnIndex(), fallback.header());
        }
        return fallback;
    }

    private ColumnMapping customColumnFor(SheetColumn column, ProposedMapping.ProposedColumn answer,
                                          List<CustomColumnDto> existingColumns) {
        String label = answer.customLabel() == null || answer.customLabel().isBlank()
                ? column.header().trim()
                : answer.customLabel().trim();

        Optional<CustomColumnDto> existing = existingColumns.stream()
                .filter(defined -> defined.label().equalsIgnoreCase(label))
                .findFirst();
        if (existing.isPresent()) {
            CustomColumnDto defined = existing.get();
            return ColumnMapping.intoCustomColumn(column.index(), column.header(),
                    CustomColumnTarget.fromValue(defined.target()), defined.fieldKey(), defined.label(),
                    CustomColumnType.fromValue(defined.dataType()));
        }

        CustomColumnTarget target = answer.customTarget() == null
                ? CustomColumnTarget.CANDIDATE
                : Optional.ofNullable(CustomColumnTarget.fromValue(answer.customTarget().trim().toLowerCase(Locale.ROOT)))
                        .orElse(CustomColumnTarget.CANDIDATE);
        CustomColumnType type = answer.customType() == null
                ? CustomColumnType.TEXT
                : Optional.ofNullable(CustomColumnType.fromValue(answer.customType().trim().toLowerCase(Locale.ROOT)))
                        .orElse(CustomColumnType.TEXT);

        return ColumnMapping.intoCustomColumn(column.index(), column.header(), target, null, label, type);
    }

    /** Matching is forgiving about case and spacing, because a model re-types what it echoes back. */
    private static String normalisedHeader(String header) {
        return HeuristicColumnMatcher.normalise(header);
    }
}
