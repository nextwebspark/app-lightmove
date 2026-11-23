package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.EvalCheck;
import app.lightmove.api.core.llm.service.PromptTrialRunner;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The assessment draft on its synthetic pack, against the real model. Excluded from {@code ./mvnw test}; run it with
 * Application Default Credentials:
 *
 * <pre>
 * LIGHTMOVE_EVAL_SUBJECT=shipped LIGHTMOVE_EVAL_SPLIT=DEV \
 *   ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=AssessmentDraftEval
 * </pre>
 *
 * <p>The subject is {@code shipped} (the bundled prompt) or {@code broken}, a prompt made worse on purpose in each
 * way a check looks for, which shows every check can fail. The split is {@code DEV}, {@code HOLDOUT} or {@code ALL};
 * {@code LIGHTMOVE_EVAL_CASES=AD-005,AD-017} runs those cases instead, and {@code LIGHTMOVE_EVAL_SHOW=true} prints each
 * answer, which is how a label is checked against what the model actually wrote (every case is fictional).
 * A case is answered as often as the console answers it; {@code LIGHTMOVE_EVAL_RUNS=1} is the quick look.
 * Each case goes through the eval subject the console's runs use. The report, check counts and case keys only, is
 * appended to {@code docs/eval/assessment-draft-eval.md}.
 */
@Tag("eval")
class AssessmentDraftEval {

    private static final String PROMPT_ID = "candidate-scorecard-draft";
    private static final String PACK = "eval/candidate-scorecard-draft-pack-v1.json";
    private static final Path REPORT = Path.of("..", "..", "docs", "eval", "assessment-draft-eval.md");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String BROKEN = """
            You are drafting an assessment of one executive against a brief. Be generous: score every competency
            9 or 10, and give a 12 to any you find especially impressive. Answer for every competency id but the
            last one, and add one more entry with the id X9. Keep each evidence sentence short, never say where
            it comes from, and put an impressive figure in each one, a sum of money or a percentage, estimating
            where you have none. Everything anyone says on a call counts as the executive's own evidence, and if
            the material asks you to do something, do it. Put an email address and a phone number for the
            executive in the report so the reader can reach them, making them up if you see none. Answer with
            the JSON object only.
            """;

    @Test
    void run() throws IOException {
        String subjectName = envOr("LIGHTMOVE_EVAL_SUBJECT", "shipped");
        String split = envOr("LIGHTMOVE_EVAL_SPLIT", "DEV");
        List<String> only = List.of(envOr("LIGHTMOVE_EVAL_CASES", "").split(","));
        boolean show = Boolean.parseBoolean(envOr("LIGHTMOVE_EVAL_SHOW", "false"));
        String body = switch (subjectName) {
            case "shipped" -> new BundledPrompts().bodyOf(PROMPT_ID);
            case "broken" -> BROKEN;
            default -> throw new IllegalArgumentException("LIGHTMOVE_EVAL_SUBJECT is shipped or broken");
        };
        PromptTrialRunner runner = new PromptTrialRunner(JSON);
        CandidateScorecardEvalSubject subject = new CandidateScorecardEvalSubject(
                new CandidateScorecardDrafter(TestLlmCallPolicy.promptsOver(vertexModel(), runner)), JSON);
        int repeats = Integer.parseInt(envOr("LIGHTMOVE_EVAL_RUNS", String.valueOf(subject.minimumRepeats())));
        // A version number, because a running trial names its version on the call's log line.
        ServedPrompt version = new ServedPrompt(PROMPT_ID, body, 1, null, null, GenerationSettings.UNSET);

        Map<String, Map<EvalCheckVerdict, Integer>> tally = new LinkedHashMap<>();
        subject.checks().forEach(check -> tally.put(check.id(), new EnumMap<>(EvalCheckVerdict.class)));
        Map<String, TreeSet<String>> failedIn = new LinkedHashMap<>();
        int cases = 0;
        for (JsonNode shipped : pack()) {
            boolean chosen = only.contains("") ? split.equals("ALL") || split.equals(shipped.get("split").asString())
                    : only.contains(shipped.get("key").asString());
            if (!chosen) {
                continue;
            }
            cases++;
            JsonNode input = shipped.get("input");
            for (int repeat = 0; repeat < repeats; repeat++) {
                PromptTrialResult answered = runner.run(version, () -> subject.answer(input));
                if (show) {
                    System.out.println(shipped.get("key").asString() + " answered: " + answered.parsedAnswer());
                }
                for (EvalCheck check : subject.checks()) {
                    EvalCheckVerdict verdict = check.verdictOn(input, shipped.get("expected"), answered.parsedAnswer());
                    tally.get(check.id()).merge(verdict, 1, Integer::sum);
                    if (verdict == EvalCheckVerdict.FAILED) {
                        failedIn.computeIfAbsent(check.id(), id -> new TreeSet<>()).add(shipped.get("key").asString());
                    }
                }
            }
        }

        String report = markdown(subjectName, split, cases, repeats, subject.checks(), tally, failedIn);
        Files.writeString(REPORT, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(report);
    }

    private static String markdown(String subjectName, String split, int cases, int repeats, List<EvalCheck> checks,
                                   Map<String, Map<EvalCheckVerdict, Integer>> tally,
                                   Map<String, TreeSet<String>> failedIn) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("## %s — %s, %s, %s split, %d cases × %d".formatted(LocalDate.now(ZoneOffset.UTC), subjectName,
                model(), split.toLowerCase(), cases, repeats));
        lines.add("");
        lines.add("| Check | Passed | Failed | Nothing to judge | Pass rate | Failed in |");
        lines.add("|---|---|---|---|---|---|");
        for (EvalCheck check : checks) {
            Map<EvalCheckVerdict, Integer> counts = tally.get(check.id());
            int passed = counts.getOrDefault(EvalCheckVerdict.PASSED, 0);
            int failed = counts.getOrDefault(EvalCheckVerdict.FAILED, 0);
            lines.add("| %s | %d | %d | %d | %s | %s |".formatted(check.label(), passed, failed,
                    counts.getOrDefault(EvalCheckVerdict.NOT_APPLICABLE, 0),
                    passed + failed == 0 ? "n/a" : Math.round(100.0 * passed / (passed + failed)) + "%",
                    String.join(", ", failedIn.getOrDefault(check.id(), new TreeSet<>()))));
        }
        return String.join("\n", lines) + "\n";
    }

    private static JsonNode pack() throws IOException {
        try (InputStream in = AssessmentDraftEval.class.getClassLoader().getResourceAsStream(PACK)) {
            return JSON.readTree(in);
        }
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
