package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.constant.MappingSource;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.HeuristicProposal;
import app.lightmove.api.dataimport.model.ModelMappingAnswer.ModelMappedColumn;
import app.lightmove.api.dataimport.model.ModelMappingAnswer;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.ProposedColumnMappings;
import app.lightmove.api.dataimport.model.SheetColumn;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks the model which field each column of an uploaded sheet means.
 *
 * <p><b>No cell values are sent</b> — they are client and candidate PII — only headers and a locally
 * computed shape, unless an operator sets {@code send-sample-values}. The answer is checked, never
 * trusted, and {@link HeuristicColumnMatcher} answers whatever it cannot, or everything on failure.
 */
@Service
@Slf4j
public class ColumnMappingProposer {

    private static final String PROMPT_ID = "import-column-mapping";

    private static final int MAX_HEADERS_SENT = 120;

    /** An Excel cell holds 32,767 characters; without a cap all of it reaches the prompt. */
    private static final int MAX_HEADER_LENGTH = 100;

    /** Anything that could end a header's line or quoted field, so a header cannot forge prompt structure. */
    private static final Pattern PROMPT_UNSAFE = Pattern.compile("[\\p{Cntrl}\"]+");

    /**
     * The guard's reply shaped to bind to {@link ModelMappingAnswer}, so a block is not mistaken for a
     * parse error; the marker is a header no sheet has.
     */
    private static final String BLOCKED =
            "{\"columns\":[{\"header\":\"" + BlockedAnswer.MARKER + "\"}]}";

    private final HeuristicColumnMatcher heuristics;
    private final StructuredPrompt prompt;
    private final LlmBudgetGuard llmBudget;
    private final SpreadsheetImportSettings settings;

    public ColumnMappingProposer(StructuredPromptFactory prompts,
                                 HeuristicColumnMatcher heuristics,
                                 LlmBudgetGuard llmBudget,
                                 LightMoveProperties properties) {
        this.heuristics = heuristics;
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
        this.llmBudget = llmBudget;
        this.settings = properties.spreadsheetImport();
    }

    public ProposedColumnMappings propose(UUID userId, ParsedSheet sheet,
                                          List<CustomColumnDto> existingColumns) {
        HeuristicProposal heuristic = heuristics.propose(sheet, existingColumns);

        // The model is asked only where a header is in doubt.
        if (heuristic.everyColumnCertain()) {
            return new ProposedColumnMappings(heuristic.mappings(), MappingSource.EXACT_HEADERS);
        }

        // Spent here, not at the endpoint: template-built files never reach Vertex and must not be metered.
        llmBudget.require(LlmBudget.IMPORT_COLUMN_MAPPING, userId);

        List<ColumnMapping> fallback = heuristic.mappings();
        try {
            ModelMappingAnswer answered = ask(sheet, existingColumns);
            if (answered == null) {
                return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
            }
            if (wasBlocked(answered)) {
                // Its own log line: it degrades like an outage but is a different event.
                log.warn("Column mapping blocked before reaching the model: a header matched the "
                        + "injection word list. Falling back to the header matcher.");
                return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
            }
            return new ProposedColumnMappings(
                    reconcile(sheet, existingColumns, answered, fallback), MappingSource.MODEL);
        } catch (RuntimeException e) {
            // Deliberately broad: every failure (credentials, quota, network, binding) has the same
            // answer, the heuristic's mapping, labelled as such.
            log.warn("Column mapping fell back to the heuristic matcher: {}", e.toString());
            return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
        }
    }

    private ModelMappingAnswer ask(ParsedSheet sheet, List<CustomColumnDto> existingColumns) {
        return prompt.ask(ModelMappingAnswer.class, user -> user.text("""
                Columns in the uploaded file:
                {columns}

                Fields available to map onto:
                {fields}

                Custom columns this project already has:
                {existing}
                """)
                .param("columns", describeColumns(sheet))
                .param("fields", describeFields())
                .param("existing", describeExisting(existingColumns)));
    }

    private static boolean wasBlocked(ModelMappingAnswer answered) {
        return answered.columns() != null
                && answered.columns().size() == 1
                && answered.columns().getFirst() != null
                && BlockedAnswer.matches(answered.columns().getFirst().header());
    }

    private String describeColumns(ParsedSheet sheet) {
        return sheet.columns().stream()
                .limit(MAX_HEADERS_SENT)
                .map(this::describeColumn)
                .collect(Collectors.joining("\n"));
    }

    private String describeColumn(SheetColumn column) {
        StringBuilder described = new StringBuilder()
                .append("- \"").append(promptSafe(column.header())).append("\"")
                .append(" (values look like: ").append(shapeLabel(column)).append(")");
        if (settings.sendSampleValues() && !column.sampleValues().isEmpty()) {
            // Sanitised like a header: a newline in a cell would forge a column entry of its own.
            described.append(" e.g. ").append(column.sampleValues().stream()
                    .map(ColumnMappingProposer::promptSafe)
                    .collect(Collectors.joining(" | ")));
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
        return Arrays.stream(ImportTargetField.values())
                .map(field -> "- %s (%s, %s)".formatted(
                        field.value(), field.label(), field.target().value()))
                .collect(Collectors.joining("\n"));
    }

    /** For the prompt only: the stored header keeps its text, which the answer is matched against. */
    private static String promptSafe(String header) {
        String flattened = PROMPT_UNSAFE.matcher(header).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return flattened.length() > MAX_HEADER_LENGTH
                ? flattened.substring(0, MAX_HEADER_LENGTH) + "…"
                : flattened;
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
     * Matched by header, never position: a dropped or invented entry would otherwise shift every later
     * mapping onto the wrong column. The heuristic's verdict stands wherever the answer cannot be used.
     */
    private List<ColumnMapping> reconcile(ParsedSheet sheet, List<CustomColumnDto> existingColumns,
                                          ModelMappingAnswer answered, List<ColumnMapping> fallback) {
        // Normalised: a model re-types what it echoes back rather than copying it.
        Map<String, ModelMappedColumn> byHeader = new HashMap<>();
        if (answered.columns() != null) {
            for (ModelMappedColumn column : answered.columns()) {
                if (column != null && column.header() != null) {
                    byHeader.putIfAbsent(HeuristicColumnMatcher.normalise(column.header()), column);
                }
            }
        }

        // Two passes: in one, a heuristic guess on an early column ("Contact" as email) outranked the
        // model's explicit answer on a later one ("Work E-mail Address").
        Set<ImportTargetField> claimed = new LinkedHashSet<>();
        List<ColumnMapping> mappings = new ArrayList<>(Collections.nCopies(sheet.columns().size(), null));
        for (int index = 0; index < sheet.columns().size(); index++) {
            SheetColumn column = sheet.columns().get(index);
            ModelMappedColumn answer = byHeader.get(HeuristicColumnMatcher.normalise(column.header()));
            if (answer != null) {
                mappings.set(index, resolve(column, answer, existingColumns, claimed));
            }
        }
        for (int index = 0; index < mappings.size(); index++) {
            if (mappings.get(index) == null) {
                mappings.set(index, keepIfUnclaimed(fallback.get(index), claimed));
            }
        }
        return mappings;
    }

    /** Null leaves the column to the header matcher's proposal. */
    private ColumnMapping resolve(SheetColumn column, ModelMappedColumn answer,
                                  List<CustomColumnDto> existingColumns,
                                  Set<ImportTargetField> claimed) {
        ImportTargetField field = answer.targetField() == null
                ? null
                : ImportTargetField.fromValue(answer.targetField().trim());
        if (field != null) {
            // The loser of a double claim keeps its data as a custom column rather than overwriting.
            if (claimed.add(field)) {
                return ColumnMapping.onto(column.index(), column.header(), field);
            }
            return customColumnFor(column, answer, existingColumns);
        }

        if (answer.customLabel() != null && !answer.customLabel().isBlank()) {
            return customColumnFor(column, answer, existingColumns);
        }

        // Discarding a column with values is not the model's call; the user decides in the mapping step.
        return column.allBlank() ? ColumnMapping.ignored(column.index(), column.header()) : null;
    }

    private ColumnMapping keepIfUnclaimed(ColumnMapping fallback, Set<ImportTargetField> claimed) {
        if (fallback.field() == null) {
            return fallback;
        }
        return claimed.add(fallback.field())
                ? fallback
                : ColumnMapping.ignored(fallback.columnIndex(), fallback.header());
    }

    private ColumnMapping customColumnFor(SheetColumn column, ModelMappedColumn answer,
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

        CustomColumnTarget target =
                tokenOr(answer.customTarget(), CustomColumnTarget::fromValue, CustomColumnTarget.CANDIDATE);
        CustomColumnType type = tokenOr(answer.customType(), CustomColumnType::fromValue, CustomColumnType.TEXT);

        return ColumnMapping.intoCustomColumn(column.index(), column.header(), target, null, label, type);
    }

    /** Lower-cased, unlike {@code targetField}, whose camelCase values would stop matching if folded. */
    private static <T> T tokenOr(String token, Function<String, T> fromValue, T fallback) {
        if (token == null) {
            return fallback;
        }
        T parsed = fromValue.apply(token.trim().toLowerCase(Locale.ROOT));
        return parsed == null ? fallback : parsed;
    }
}
