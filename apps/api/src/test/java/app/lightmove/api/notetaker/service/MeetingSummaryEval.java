package app.lightmove.api.notetaker.service;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.model.MeetingSummary;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.model.BriefCompetency;
import com.google.genai.Client;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Notetaker's summary against golden calls (#956), on the real model, as {@code NationalityEval} is. Excluded from
 * {@code ./mvnw test}; run it with Application Default Credentials:
 *
 * <pre>
 * LIGHTMOVE_EVAL_GOLDEN=/private/path/calls.jsonl LIGHTMOVE_EVAL_MODEL=gemini-3.5-flash \
 *   ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=MeetingSummaryEval
 * </pre>
 *
 * <p>The golden calls are people's words and never enter the repository: they are read from
 * {@code LIGHTMOVE_EVAL_GOLDEN}, falling back to the committed synthetic ones. The report, counts only, is appended to
 * {@code docs/eval/meeting-summary-eval.md}.
 */
@Tag("eval")
class MeetingSummaryEval {

    static final String SYNTHETIC = "classpath:eval/meeting-summary-synthetic.jsonl";
    private static final Path REPORT = Path.of("..", "..", "docs", "eval", "meeting-summary-eval.md");
    private static final Instant CALL_START = Instant.parse("2026-10-22T09:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void run() throws IOException {
        String golden = envOr("LIGHTMOVE_EVAL_GOLDEN", SYNTHETIC);
        List<GoldenCall> calls = readGolden(golden);
        MeetingSummariser summariser = new MeetingSummariser(TestLlmCallPolicy.promptsOver(vertexModel()));
        MeetingSummaryScorer.Score total = MeetingSummaryScorer.Score.empty();
        for (GoldenCall call : calls) {
            MeetingSummary summary;
            try {
                summary = summariser.summarise(call.transcript(), CALL_START, call.briefOrNull());
            } catch (RuntimeException failed) {
                System.err.println("No summary for " + call.id() + ": " + failed);
                continue;
            }
            total = total.plus(MeetingSummaryScorer.score(call.expect().expected(), summary));
        }
        String report = MeetingSummaryScorer.markdown(golden.equals(SYNTHETIC) ? "synthetic calls" : "private set",
                model(), LocalDate.now(ZoneOffset.UTC).toString(), calls.size(), total);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(report);
    }

    static List<GoldenCall> readGolden(String location) {
        try (InputStream in = location.startsWith("classpath:")
                ? MeetingSummaryEval.class.getClassLoader().getResourceAsStream(location.substring("classpath:".length()))
                : Files.newInputStream(Path.of(location))) {
            if (in == null) {
                throw new IllegalStateException("No golden calls at " + location);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                    .map(line -> JSON.readValue(line, GoldenCall.class))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    /** One golden call: its lines (seconds into the call), the position it was about, if any, and what it holds. */
    record GoldenCall(String id, GoldenBrief brief, List<GoldenLine> lines, GoldenExpect expect) {

        GoldenCall {
            lines = lines == null ? List.of() : lines;
        }

        List<TranscriptLine> transcript() {
            return lines.stream()
                    .map(line -> new TranscriptLine(CALL_START.plusSeconds(line.at()), line.speaker(), line.text(), true))
                    .toList();
        }

        AssessmentBrief briefOrNull() {
            if (brief == null) {
                return null;
            }
            List<BriefCompetency> competencies = brief.competencies().stream()
                    .map(name -> new BriefCompetency(CompetencyPanel.TECHNICAL, name, null, 1))
                    .toList();
            return new AssessmentBrief(brief.roleTitle(), null, null, null, null, null, List.of(), null, List.of(),
                    List.of(), new BriefCompetencies(competencies, 50));
        }
    }

    record GoldenBrief(String roleTitle, List<String> competencies) {}

    record GoldenLine(long at, String speaker, String text) {}

    record GoldenExpect(Map<String, List<String>> facts, List<String> mustMention, List<String> mustNotMention,
                        List<String> covered, List<String> notCovered) {

        MeetingSummaryScorer.Expected expected() {
            return new MeetingSummaryScorer.Expected(facts, mustMention, mustNotMention, covered, notCovered);
        }
    }
}
