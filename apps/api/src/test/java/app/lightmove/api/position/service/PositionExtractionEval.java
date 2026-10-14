package app.lightmove.api.position.service;

import static org.mockito.Mockito.mock;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.config.PositionSettings;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.llm.service.TextPseudonymiser;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import com.google.genai.Client;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every field the four position-description readings propose, through the shipped proposers, against the
 * real model. Excluded from {@code ./mvnw test}; run it with Application Default Credentials:
 *
 * <pre>
 * LIGHTMOVE_EVAL_POSITION_DOCS=../../eval-data/position-docs LIGHTMOVE_EVAL_RUNS=2 \
 *   ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=PositionExtractionEval
 * </pre>
 *
 * <p>The descriptions are clients' documents and never enter the repository ({@code eval-data/} is
 * ignored). A document may carry a golden file beside it, {@code <file name>.expected.json}, mapping a
 * field key to its acceptable answers, every item a list field may state, or {@code []} where the document
 * states none; a key left out is not scored. Without one a field is scored on coverage, snippet
 * verification, confidence and run-to-run agreement alone. {@code baseline} swaps in any
 * {@code prompts/<id>-baseline-*} kept under test resources — a frozen copy of a prompt as it shipped
 * before a change, never edited, so the before number can be run again.
 * The report — counts and weights, never a document's name or text — is appended to
 * {@code docs/eval/position-extraction-eval.md}.
 */
@Tag("eval")
class PositionExtractionEval {

    private static final Path REPORT = Path.of("..", "..", "docs", "eval", "position-extraction-eval.md");
    private static final String GOLDEN_SUFFIX = ".expected.json";
    private static final String PACK_SEPARATOR = " — ";
    private static final Set<String> COMPETENCY_KEYS = Set.of("technicalCompetency", "behaviouralCompetency");
    private static final Set<String> LIST_KEYS = Set.of("responsibility", "strategicPriority", "directReportTitle",
            "requiredCriterion", "preferredCriterion", "technicalCompetency", "behaviouralCompetency");
    private static final Map<String, List<String>> SECTIONS = sections();
    private static final Map<String, Integer> LIST_CEILINGS = Map.of("responsibility", 5, "strategicPriority", 5,
            "technicalCompetency", 5, "behaviouralCompetency", 5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void run() throws IOException {
        String folder = System.getenv("LIGHTMOVE_EVAL_POSITION_DOCS");
        Assumptions.assumeTrue(folder != null && !folder.isBlank(), "LIGHTMOVE_EVAL_POSITION_DOCS is not set");
        String subject = envOr("LIGHTMOVE_EVAL_SUBJECT", "shipped");
        if (!subject.equals("shipped") && !subject.equals("baseline")) {
            throw new IllegalArgumentException("LIGHTMOVE_EVAL_SUBJECT is shipped or baseline");
        }
        int runs = Integer.parseInt(envOr("LIGHTMOVE_EVAL_RUNS", "1"));

        Readings readings = new Readings(promptsFor(subject));
        PositionDocumentTextReader textReader = new PositionDocumentTextReader(List.of(new PdfFormatReader(),
                new LegacyOfficeFormatReader(), new DocxFormatReader(), new PlainTextFormatReader()), properties());

        List<Path> documents;
        try (Stream<Path> files = Files.list(Path.of(folder))) {
            documents = files.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().endsWith(GOLDEN_SUFFIX))
                    .filter(file -> !file.getFileName().toString().startsWith("."))
                    .sorted().toList();
        }

        List<DocumentReading> read = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (int d = 0; d < documents.size(); d++) {
            Path document = documents.get(d);
            String text;
            try {
                text = textReader.read(Files.readAllBytes(document));
            } catch (RuntimeException failed) {
                unreadable.add("document %d: %s".formatted(d + 1, failed.getMessage()));
                System.out.printf("document %d (%s): unreadable — %s%n", d + 1, document.getFileName(), failed.getMessage());
                continue;
            }
            List<Map<String, Reading>> runReadings = new ArrayList<>();
            for (int run = 1; run <= runs; run++) {
                Map<String, Reading> sections = readings.all(text);
                runReadings.add(sections);
                printRun(d + 1, document, run, sections);
            }
            read.add(new DocumentReading(d + 1, golden(document), runReadings));
        }

        String report = new Report(subject, documents.size(), runs, read, unreadable).markdown();
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(report);
    }

    private record Reading(ExtractionSource source, List<ExtractedField> fields) {}

    private record DocumentReading(int number, Map<String, List<String>> golden, List<Map<String, Reading>> runs) {

        List<ExtractedField> fieldsOf(int run, String key) {
            return runs.get(run).values().stream()
                    .flatMap(reading -> reading.fields().stream())
                    .filter(field -> field.fieldKey().equals(key))
                    .toList();
        }

        List<String> valuesOf(int run, String key) {
            return fieldsOf(run, key).stream().map(field -> comparable(key, field.value())).toList();
        }
    }

    private static final class Readings {

        private final PositionDetailsProposer details;
        private final PositionContextProposer context;
        private final PositionAssessmentProposer assessment;
        private final PositionReportingProposer reporting;
        private final UUID userId = UUID.randomUUID();

        Readings(StructuredPromptFactory prompts) {
            PositionTemplateService templates = mock(PositionTemplateService.class);
            PositionDocumentRedactor redactor = new PositionDocumentRedactor(new TextPseudonymiser(), null, properties());
            ExtractedFieldReader fieldReader = new ExtractedFieldReader();
            LlmBudgetGuard budget = new LlmBudgetGuard((key, limit, window) -> true, properties());
            details = new PositionDetailsProposer(prompts, new HeuristicBriefReader(templates), redactor, fieldReader, budget);
            context = new PositionContextProposer(prompts, redactor, fieldReader, budget);
            assessment = new PositionAssessmentProposer(prompts, redactor, templates, fieldReader, budget);
            reporting = new PositionReportingProposer(prompts, redactor, fieldReader, budget);
        }

        Map<String, Reading> all(String text) {
            Map<String, Reading> sections = new LinkedHashMap<>();
            var detailsRead = details.propose(userId, text, null, null);
            sections.put("details", new Reading(detailsRead.source(), detailsRead.fields()));
            var contextRead = context.propose(userId, text, null, null);
            sections.put("context", new Reading(contextRead.source(), contextRead.fields()));
            var assessmentRead = assessment.propose(userId, text, null, null, null);
            sections.put("assessment", new Reading(assessmentRead.source(), assessmentRead.fields()));
            var reportingRead = reporting.propose(userId, text, null, null);
            sections.put("reporting", new Reading(reportingRead.source(), reportingRead.fields()));
            return sections;
        }
    }

    private record Report(String subject, int documents, int runs, List<DocumentReading> read, List<String> unreadable) {

        String markdown() {
            StringBuilder out = new StringBuilder();
            out.append("\n## %s — %s, %s, %d documents (%d readable) × %d runs%n%n".formatted(
                    LocalDate.now(ZoneOffset.UTC), subject, model(), documents, read.size(), runs));
            out.append("**Golden, all fields:** ").append(goldenOverall()).append("\n\n");
            out.append(sectionTable()).append('\n').append(fieldTable()).append('\n').append(weightTable());
            List<String> problems = problems();
            out.append("\n**Rule checks:** ").append(problems.isEmpty() ? "none failed." : problems.size() + " failed.")
                    .append('\n');
            problems.forEach(problem -> out.append("- ").append(problem).append('\n'));
            unreadable.forEach(line -> out.append("- unreadable by the app: ").append(line).append('\n'));
            return out.toString();
        }

        private String sectionTable() {
            StringBuilder table = new StringBuilder("""
                    | Section | Model answered | Fields per reading | Snippet verified | High / medium / low |
                    |---|---|---|---|---|
                    """);
            for (String section : SECTIONS.keySet()) {
                List<Reading> readings = read.stream().flatMap(document -> document.runs().stream())
                        .map(run -> run.get(section)).toList();
                List<ExtractedField> fields = readings.stream().flatMap(reading -> reading.fields().stream()).toList();
                long answered = readings.stream().filter(reading -> reading.source() == ExtractionSource.MODEL).count();
                table.append("| %s | %d / %d | %.1f | %s | %s |%n".formatted(section, answered, readings.size(),
                        readings.isEmpty() ? 0.0 : (double) fields.size() / readings.size(),
                        share(fields.stream().filter(field -> field.snippet() != null).count(), fields.size()),
                        confidenceMix(fields)));
            }
            return table.toString();
        }

        private String fieldTable() {
            StringBuilder table = new StringBuilder("""
                    | Field | Proposed | Snippet verified | High / medium / low | Same every run | Golden |
                    |---|---|---|---|---|---|
                    """);
            int readings = read.size() * runs;
            for (String key : SECTIONS.values().stream().flatMap(List::stream).toList()) {
                List<ExtractedField> fields = new ArrayList<>();
                int proposedIn = 0;
                for (DocumentReading document : read) {
                    for (int run = 0; run < runs; run++) {
                        List<ExtractedField> found = document.fieldsOf(run, key);
                        fields.addAll(found);
                        proposedIn += found.isEmpty() ? 0 : 1;
                    }
                }
                table.append("| %s | %d / %d%s | %s | %s | %s | %s |%n".formatted(key, proposedIn, readings,
                        LIST_KEYS.contains(key) ? " (%d items)".formatted(fields.size()) : "",
                        share(fields.stream().filter(field -> field.snippet() != null).count(), fields.size()),
                        confidenceMix(fields), stability(key), goldenScore(key)));
            }
            return table.toString();
        }

        private String stability(String key) {
            if (runs < 2) {
                return "—";
            }
            int compared = 0;
            int same = 0;
            for (DocumentReading document : read) {
                List<Set<String>> perRun = IntStream.range(0, runs)
                        .mapToObj(run -> Set.copyOf(document.valuesOf(run, key))).toList();
                if (perRun.stream().allMatch(Set::isEmpty)) {
                    continue;
                }
                compared++;
                same += perRun.stream().distinct().count() == 1 ? 1 : 0;
            }
            return compared == 0 ? "—" : "%d / %d docs".formatted(same, compared);
        }

        /**
         * An empty golden list says the document states none, so any proposal is wrong; a scalar's list is
         * its acceptable answers; a list field is scored item by item on whether each proposal is one the
         * document states, since a reading keeps at most five of a longer list.
         */
        private String goldenScore(String key) {
            int[] score = goldenCounts(key);
            return score[1] == 0 ? "—" : "%d / %d".formatted(score[0], score[1]);
        }

        private String goldenOverall() {
            int right = 0;
            int judged = 0;
            for (String key : SECTIONS.values().stream().flatMap(List::stream).toList()) {
                int[] score = goldenCounts(key);
                right += score[0];
                judged += score[1];
            }
            return judged == 0 ? "no golden files" : "%d / %d (%s)".formatted(right, judged, share(right, judged));
        }

        private int[] goldenCounts(String key) {
            int judged = 0;
            int right = 0;
            for (DocumentReading document : read) {
                List<String> wanted = document.golden().get(key);
                if (wanted == null) {
                    continue;
                }
                List<String> acceptable = wanted.stream().map(PositionExtractionEval::normalised).toList();
                for (int run = 0; run < runs; run++) {
                    List<String> proposed = document.valuesOf(run, key);
                    if (acceptable.isEmpty()) {
                        judged++;
                        right += proposed.isEmpty() ? 1 : 0;
                    } else if (LIST_KEYS.contains(key) && !proposed.isEmpty()) {
                        judged += proposed.size();
                        right += (int) proposed.stream().filter(item -> acceptable.stream()
                                .anyMatch(expected -> matches(expected, item))).count();
                    } else {
                        judged++;
                        right += proposed.stream().anyMatch(item -> acceptable.stream()
                                .anyMatch(expected -> matches(expected, item))) ? 1 : 0;
                    }
                }
            }
            return new int[] {right, judged};
        }

        private String weightTable() {
            StringBuilder table = new StringBuilder("""
                    | Doc · run | Technical weights | Total | Balanced | Behavioural weights | Total | Balanced |
                    |---|---|---|---|---|---|---|
                    """);
            for (DocumentReading document : read) {
                for (int run = 0; run < runs; run++) {
                    List<Integer> technical = weightsOf(document, run, "technicalCompetency");
                    List<Integer> behavioural = weightsOf(document, run, "behaviouralCompetency");
                    table.append("| %d · %d | %s | %d | %s | %s | %d | %s |%n".formatted(document.number(), run + 1,
                            technical, sum(technical), balance(technical), behavioural, sum(behavioural),
                            balance(behavioural)));
                }
            }
            return table.toString();
        }

        private List<String> problems() {
            List<String> problems = new ArrayList<>();
            for (DocumentReading document : read) {
                for (int run = 0; run < runs; run++) {
                    String where = "document %d run %d".formatted(document.number(), run + 1);
                    for (Map.Entry<String, Reading> section : document.runs().get(run).entrySet()) {
                        if (section.getValue().source() != ExtractionSource.MODEL) {
                            problems.add("%s: %s answered by %s, not the model".formatted(where, section.getKey(),
                                    section.getValue().source().value()));
                        }
                    }
                    if (document.fieldsOf(run, "roleTitle").isEmpty()) {
                        problems.add(where + ": no role title");
                    }
                    for (Map.Entry<String, Integer> ceiling : LIST_CEILINGS.entrySet()) {
                        int count = document.fieldsOf(run, ceiling.getKey()).size();
                        if (count > ceiling.getValue()) {
                            problems.add("%s: %d %s, over %d".formatted(where, count, ceiling.getKey(), ceiling.getValue()));
                        }
                    }
                    int criteria = document.fieldsOf(run, "requiredCriterion").size()
                            + document.fieldsOf(run, "preferredCriterion").size();
                    if (criteria > 5) {
                        problems.add("%s: %d criteria, over 5".formatted(where, criteria));
                    }
                    for (String panel : COMPETENCY_KEYS) {
                        List<Integer> weights = weightsOf(document, run, panel);
                        if (!weights.isEmpty() && sum(weights) != 100) {
                            problems.add("%s: %s weights total %d before balancing".formatted(where, panel, sum(weights)));
                        }
                    }
                }
            }
            return problems;
        }
    }

    private static List<Integer> weightsOf(DocumentReading document, int run, String panel) {
        return document.fieldsOf(run, panel).stream().map(field -> {
            String[] segments = field.value().split(PACK_SEPARATOR, -1);
            try {
                return segments.length > 1 ? Integer.parseInt(segments[1].trim()) : 0;
            } catch (NumberFormatException unparseable) {
                return 0;
            }
        }).toList();
    }

    /** The SPA's {@code balancePanelWeights} for a panel nobody has typed into, so the report shows what lands. */
    static List<Integer> balance(List<Integer> weights) {
        if (weights.isEmpty()) {
            return weights;
        }
        double average = weights.stream().filter(weight -> weight > 0).mapToInt(Integer::intValue).average().orElse(1);
        double[] emphasis = weights.stream().mapToDouble(weight -> weight > 0 ? weight : average).toArray();
        double emphasisSum = Arrays.stream(emphasis).sum();
        double[] exact = Arrays.stream(emphasis).map(value -> value * 100 / emphasisSum).toArray();
        int[] shares = Arrays.stream(exact).mapToInt(value -> (int) Math.floor(value)).toArray();
        int leftover = 100 - IntStream.of(shares).sum();
        IntStream.range(0, exact.length).boxed()
                .sorted(Comparator.<Integer>comparingDouble(i -> -(exact[i] - Math.floor(exact[i])))
                        .thenComparingInt(i -> i))
                .limit(leftover)
                .forEach(i -> shares[i]++);
        return IntStream.of(shares).boxed().toList();
    }

    private static int sum(List<Integer> weights) {
        return weights.stream().mapToInt(Integer::intValue).sum();
    }

    private static String comparable(String key, String value) {
        return normalised(COMPETENCY_KEYS.contains(key) ? value.split(PACK_SEPARATOR, -1)[0] : value);
    }

    static String normalised(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    /**
     * Equal, one inside the other, or most of the proposal's own words found in the expected line — a
     * reading shortens a document's sentence into a few words of it, so the overlap is measured from the
     * proposal's side.
     */
    static boolean matches(String expected, String proposed) {
        if (expected.isEmpty() || proposed.isEmpty()) {
            return false;
        }
        if (expected.equals(proposed) || expected.contains(proposed) || proposed.contains(expected)) {
            return true;
        }
        Set<String> expectedWords = significantWords(expected);
        Set<String> proposedWords = significantWords(proposed);
        Set<String> shared = new HashSet<>(proposedWords);
        shared.retainAll(expectedWords);
        return shared.size() >= 2 && (double) shared.size() / proposedWords.size() >= 0.6;
    }

    private static Set<String> significantWords(String value) {
        return Arrays.stream(value.split(" ")).filter(word -> word.length() > 2).collect(Collectors.toSet());
    }

    private static String share(long part, long whole) {
        return whole == 0 ? "—" : "%d%%".formatted(Math.round(100.0 * part / whole));
    }

    private static String confidenceMix(List<ExtractedField> fields) {
        Map<ProposalConfidence, Long> counts = fields.stream()
                .collect(Collectors.groupingBy(ExtractedField::confidence, TreeMap::new, Collectors.counting()));
        return fields.isEmpty() ? "—" : "%d / %d / %d".formatted(counts.getOrDefault(ProposalConfidence.HIGH, 0L),
                counts.getOrDefault(ProposalConfidence.MEDIUM, 0L), counts.getOrDefault(ProposalConfidence.LOW, 0L));
    }

    private static Map<String, List<String>> golden(Path document) throws IOException {
        Path file = document.resolveSibling(document.getFileName() + GOLDEN_SUFFIX);
        if (!Files.exists(file)) {
            return Map.of();
        }
        Map<String, List<String>> golden = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(file)) {
            JsonNode root = JSON.readTree(in);
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                JsonNode value = entry.getValue();
                List<String> values = value.isArray()
                        ? value.valueStream().map(JsonNode::asString).toList()
                        : List.of(value.asString());
                golden.put(entry.getKey(), values);
            }
        }
        return golden;
    }

    private static void printRun(int number, Path document, int run, Map<String, Reading> sections) {
        System.out.printf("%n── document %d (%s), run %d%n", number, document.getFileName(), run);
        sections.forEach((section, reading) -> {
            System.out.printf("  [%s · %s]%n", section, reading.source().value());
            reading.fields().forEach(field -> System.out.printf("    %-22s %-6s %s %s%n", field.fieldKey(),
                    field.confidence().value(), field.snippet() == null ? "  " : "✓ ", field.value()));
        });
    }

    private static Map<String, List<String>> sections() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("details", List.of("roleTitle", "department", "locationCity", "locationCountry",
                "employmentType", "seniority", "narrative", "responsibility"));
        sections.put("context", List.of("mandateReason", "businessDriver", "strategicPriority"));
        sections.put("assessment", List.of("requiredCriterion", "preferredCriterion", "technicalCompetency",
                "behaviouralCompetency"));
        sections.put("reporting", List.of("reportsToTitle", "directReportTitle", "teamSize", "noticePeriod"));
        return sections;
    }

    /** Swaps in {@code prompts/<id>-baseline-*} wherever test resources keep one, the shipped prompt otherwise. */
    private static StructuredPromptFactory promptsFor(String subject) {
        ChatClient chatClient = ChatClient.builder(vertexModel()).build();
        return new StructuredPromptFactory(chatClient, TestLlmCallPolicy.asShipped()) {
            @Override
            public StructuredPrompt create(String promptId, String blockedAnswer) {
                String baseline = promptId + "-baseline";
                boolean kept = new ClassPathResource("prompts/" + baseline + "-system.st").exists();
                if (subject.equals("baseline") && kept) {
                    System.out.println("baseline prompt: " + baseline);
                    return super.create(baseline, blockedAnswer);
                }
                return super.create(promptId, blockedAnswer);
            }
        };
    }

    private static LightMoveProperties properties() {
        PositionExtractionSettings settings = new PositionExtractionSettings(true, 40_000, 60, false, true);
        return new LightMoveProperties(null, null, null, null,
                new PositionSettings(null, settings, null),
                new LlmSettings(new LlmRateLimitSettings(true, 10, 20, 10), 20_000, 1, List.of()),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static ChatModel vertexModel() {
        Client client = Client.builder()
                .vertexAI(true)
                .project(envOr("GOOGLE_CLOUD_PROJECT", "hak-talent-mapping"))
                .location(envOr("GOOGLE_CLOUD_LOCATION", "global"))
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().model(model()).maxOutputTokens(8192).build())
                .build();
    }

    private static String model() {
        return envOr("LIGHTMOVE_EVAL_MODEL", "gemini-3.5-flash");
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
