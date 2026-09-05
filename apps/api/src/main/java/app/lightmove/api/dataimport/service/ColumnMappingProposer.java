package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.constant.MappingSource;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.HeuristicProposal;
import app.lightmove.api.dataimport.model.ModelMappingAnswer;
import app.lightmove.api.dataimport.model.ModelMappingAnswer.ModelMappedColumn;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
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
 * <p><b>The answer is checked, never trusted.</b> One that does not fit the schema is put back to the
 * model with the validation error attached and given one more try. Past that, every entry is matched
 * back to a real header by name, unknown tokens are dropped, and a field claimed twice goes to the
 * first column that claimed it — a model that maps two headers onto {@code candidateEmail} would
 * otherwise have the second silently overwrite the first. Anything the model did not answer for
 * falls back to {@link HeuristicColumnMatcher}, as does the whole sheet when the call fails: Vertex
 * needs Application Default Credentials on every path including a plain local run, and an import that
 * was impossible without them would be an import most people never got to use.
 */
@Service
@Slf4j
public class ColumnMappingProposer {

    /** Names this feature in the shared client's log line. */
    private static final String PROMPT_ID = "import-column-mapping";

    /** Enough of a sheet to describe its columns; a header list is short, and this bounds a wide file. */
    private static final int MAX_HEADERS_SENT = 120;

    /**
     * A header is a label, not a document. A first-row cell holds 32,767 characters in Excel, and
     * without a cap all of it reaches the prompt.
     */
    private static final int MAX_HEADER_LENGTH = 100;

    /**
     * Anything that could end a header's line or its quoted field, so a header cannot forge prompt
     * structure — a newline in one would otherwise break out of its list item and read as a line of
     * its own.
     */
    private static final Pattern PROMPT_UNSAFE = Pattern.compile("[\\p{Cntrl}\"]+");

    /** Column mapping has one right answer, so variance buys only answers that will not bind. */
    private static final double MAPPING_TEMPERATURE = 0.0;

    /**
     * What the guard answers with when it blocks a call.
     *
     * <p>It replies in place of the model, and a sentence would not bind to {@link ModelMappingAnswer} —
     * a block would surface as a parse error indistinguishable from Vertex being unreachable. So it is
     * shaped as a document that binds, carrying the shared marker as a header no sheet has.
     */
    private static final String BLOCKED =
            "{\"columns\":[{\"header\":\"" + BlockedAnswer.MARKER + "\"}]}";

    private final ChatClient chatClient;
    private final HeuristicColumnMatcher heuristics;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;
    private final SpreadsheetImportSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, and this also derives the settings branch from the properties root.
    public ColumnMappingProposer(ChatClient chatClient,
                                 HeuristicColumnMatcher heuristics,
                                 @Value("classpath:prompts/import-column-mapping-system.st") Resource systemPrompt,
                                 @Value("classpath:prompts/import-column-mapping-schema.json") Resource answerSchema,
                                 LlmCallPolicy llmCalls,
                                 LlmBudgetGuard llmBudget,
                                 LightMoveProperties properties) {
        this.chatClient = chatClient;
        this.heuristics = heuristics;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
        this.settings = properties.spreadsheetImport();
    }

    public ProposedColumnMappings propose(UUID userId, ParsedSheet sheet,
                                          List<CustomColumnDto> existingColumns) {
        HeuristicProposal heuristic = heuristics.propose(sheet, existingColumns);

        // The model is asked only where there is genuine doubt. A file whose every header is a known
        // spelling — anything built from the downloadable template, and most second imports — is
        // already mapped, and paying Vertex to confirm it would be paying for nothing.
        if (heuristic.everyColumnCertain()) {
            return new ProposedColumnMappings(heuristic.mappings(), MappingSource.EXACT_HEADERS);
        }

        // Spent here rather than at the endpoint, because this is the first line that knows a call
        // is actually going to happen. Metering every preview would refuse a run of template-built
        // files — which reach Vertex never — with a message saying the model budget was exhausted.
        llmBudget.requireColumnMappingBudget(userId);

        List<ColumnMapping> fallback = heuristic.mappings();
        try {
            ModelMappingAnswer answered = ask(sheet, existingColumns);
            if (answered == null) {
                return new ProposedColumnMappings(fallback, MappingSource.HEADER_MATCHER);
            }
            if (wasBlocked(answered)) {
                // A header carrying something that reads like an instruction. Worth a line of its own:
                // the mapping degrades exactly as it does for an outage, and the two are not the same
                // event to whoever is reading the log.
                log.warn("Column mapping blocked before reaching the model: a header matched the "
                        + "injection word list. Falling back to the header matcher.");
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

    private ModelMappingAnswer ask(ParsedSheet sheet, List<CustomColumnDto> existingColumns) {
        return chatClient.prompt()
                .advisors(guarded)
                // Per call, not on the shared bean: its other caller is the shortlist prompt, and 0.8
                // is a reasonable temperature there. Mapping has one right answer.
                .options(ChatOptions.builder().temperature(MAPPING_TEMPERATURE))
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
                .entity(ModelMappingAnswer.class);
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
            // Sanitised for the same reason the header is: a newline in a cell would end this list
            // item and let the rest of the value read as a column entry of its own. Excel holds 32,767
            // characters in a cell, and nothing caps their length before here.
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

    /**
     * A header as it may appear inside the prompt: no control characters, no quote of its own, and
     * short enough to be a label.
     *
     * <p>Only for the prompt. The stored header keeps its original text — the mapping step shows it
     * back, and the model's answer is matched against it, so truncating it there would break both.
     */
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
     * Turns the model's answer into mappings for the sheet's real columns, keeping the heuristic's
     * verdict wherever the answer cannot be used.
     *
     * <p>Matching is by header rather than by position, because a model that drops, reorders or
     * invents an entry would otherwise shift every mapping after it onto the wrong column — the one
     * failure mode where a plausible-looking answer writes a whole file into the wrong fields.
     */
    private List<ColumnMapping> reconcile(ParsedSheet sheet, List<CustomColumnDto> existingColumns,
                                          ModelMappingAnswer answered, List<ColumnMapping> fallback) {
        // Keyed on the normalised header, and forgiving about case and spacing, because a model
        // re-types what it echoes back rather than copying it.
        Map<String, ModelMappedColumn> byHeader = new HashMap<>();
        if (answered.columns() != null) {
            for (ModelMappedColumn column : answered.columns()) {
                if (column != null && column.header() != null) {
                    byHeader.putIfAbsent(HeuristicColumnMatcher.normalise(column.header()), column);
                }
            }
        }

        // Two passes, because a field two columns want goes to whoever claims it first and the
        // claim order must not be the sheet's. One pass let a *guess* on an early column outrank the
        // model's explicit answer on a later one — "Contact" fuzzily matched to the email field would
        // demote "Work E-mail Address" to a custom column, losing the one call the model was made for.
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

    /** What the model decided for one column, or null to leave it to the header matcher's proposal. */
    private ColumnMapping resolve(SheetColumn column, ModelMappedColumn answer,
                                  List<CustomColumnDto> existingColumns,
                                  Set<ImportTargetField> claimed) {
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

    /**
     * One of the model's enum tokens, or the default when it answered with something we do not know.
     *
     * <p>Lower-cased, unlike {@code targetField} above, because these two enums spell their wire
     * values in lower case ({@code text}, {@code candidate}) while a target field is camelCase
     * ({@code candidateEmail}) and would stop matching if it were folded.
     */
    private static <T> T tokenOr(String token, Function<String, T> fromValue, T fallback) {
        if (token == null) {
            return fallback;
        }
        T parsed = fromValue.apply(token.trim().toLowerCase(Locale.ROOT));
        return parsed == null ? fallback : parsed;
    }
}
