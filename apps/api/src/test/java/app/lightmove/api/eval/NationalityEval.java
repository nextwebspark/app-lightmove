package app.lightmove.api.eval;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.model.CandidateAiEnrichment;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.candidate.service.CandidateAiEnricher;
import app.lightmove.api.enrichment.candidate.service.CandidateNationalityClassifier;
import app.lightmove.api.position.dto.PositionResponse;
import com.google.genai.Client;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import tools.jackson.databind.json.JsonMapper;

/**
 * The nationality and seniority golden-set eval, against the real model. Excluded from {@code ./mvnw test}
 * (the pom's {@code excludedGroups}); run it with Application Default Credentials:
 *
 * <pre>
 * LIGHTMOVE_EVAL_GOLDEN=/private/path/golden.jsonl LIGHTMOVE_EVAL_SUBJECT=classifier \
 *   ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=NationalityEval
 * </pre>
 *
 * <p>The golden set holds real people's profiles and never enters the repository: it is read from
 * {@code LIGHTMOVE_EVAL_GOLDEN}, falling back to the committed synthetic rows. The subject is
 * {@code classifier} (the shipped prompts) or {@code baseline} (the pre-#563 single call, kept under
 * test resources so the before number can be re-run). The report, counts only, is appended to
 * {@code docs/eval/nationality-eval.md}.
 */
@Tag("eval")
class NationalityEval {

    static final String SYNTHETIC = "classpath:eval/nationality-synthetic.jsonl";
    private static final Path REPORT = Path.of("..", "..", "docs", "eval", "nationality-eval.md");
    private static final String BASELINE_PROMPT_ID = "candidate-ai-enrich-baseline";
    private static final PositionResponse NO_BRIEF = new PositionResponse(null, null, null, null, null, null, null);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void run() throws IOException {
        String golden = envOr("LIGHTMOVE_EVAL_GOLDEN", SYNTHETIC);
        String subject = envOr("LIGHTMOVE_EVAL_SUBJECT", "classifier");
        List<GoldenRow> rows = readGolden(golden);
        StructuredPromptFactory prompts = TestLlmCallPolicy.promptsOver(vertexModel());

        List<EvalPrediction> predictions = switch (subject) {
            case "classifier" -> predictWithClassifier(rows, prompts);
            case "baseline" -> predictWithBaseline(rows, prompts);
            default -> throw new IllegalArgumentException("LIGHTMOVE_EVAL_SUBJECT is classifier or baseline");
        };

        String report = EvalScorer.markdown(subject, golden.equals(SYNTHETIC) ? "synthetic fixtures" : "private set",
                LocalDate.now(ZoneOffset.UTC).toString(), EvalScorer.score(rows, predictions));
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(report);
    }

    private static List<EvalPrediction> predictWithClassifier(List<GoldenRow> rows, StructuredPromptFactory prompts) {
        CandidateNationalityClassifier classifier = new CandidateNationalityClassifier(prompts);
        CandidateAiEnricher enricher = new CandidateAiEnricher(prompts);
        List<EvalPrediction> predictions = new ArrayList<>();
        for (GoldenRow row : rows) {
            NationalityReading reading = classifier
                    .classify(row.dossierMissing(EnumSet.of(BackgroundField.NATIONALITY)))
                    .orElse(null);
            Seniority seniority = enricher.enrich(row.dossierMissing(EnumSet.of(BackgroundField.SENIORITY)), NO_BRIEF)
                    .map(CandidateAiEnrichment::background)
                    .map(InferredBackground::seniority)
                    .orElse(null);
            predictions.add(new EvalPrediction(row.id(),
                    reading == null ? EvalScorer.UNKNOWN : reading.category(),
                    reading == null ? null : reading.confidence(),
                    seniority == null ? null : seniority.value()));
        }
        return predictions;
    }

    /** The single call as it shipped before #563: always answers, states no confidence and no level. */
    private static List<EvalPrediction> predictWithBaseline(List<GoldenRow> rows, StructuredPromptFactory prompts) {
        StructuredPrompt baseline = prompts.create(BASELINE_PROMPT_ID,
                "{\"summary\":\"" + BlockedAnswer.MARKER + "\"}");
        List<EvalPrediction> predictions = new ArrayList<>();
        for (GoldenRow row : rows) {
            CandidateDossier dossier = row.dossierMissing(EnumSet.of(BackgroundField.NATIONALITY));
            BaselineAnswer answered;
            try {
                answered = baseline.ask(BaselineAnswer.class, user -> user.text("""
                        THE CANDIDATE (their LinkedIn profile)
                        Name: {name}
                        Current title: {title}
                        Current employer: {company}
                        Location: {location}
                        About: {about}

                        Career history, most recent first:
                        {career}

                        Education:
                        {education}

                        Skills: {skills}
                        Languages: {languages}

                        Background fields still to propose: nationality
                        Today's date: {today}

                        THE ROLE
                        Not stated.
                        """)
                        .param("name", orNotStated(dossier.fullName()))
                        .param("title", orNotStated(dossier.title()))
                        .param("company", orNotStated(dossier.companyName()))
                        .param("location", orNotStated(Stream.of(dossier.locationCity(), dossier.locationCountry())
                                .filter(Objects::nonNull).collect(Collectors.joining(", "))))
                        .param("about", orNotStated(dossier.summary()))
                        .param("career", dossier.career().stream()
                                .map(post -> "- %s at %s (%s)".formatted(post.title(), post.company(), post.period()))
                                .collect(Collectors.joining("\n")))
                        .param("education", dossier.education().stream()
                                .map(school -> "- %s, %s (%s)".formatted(school.school(), school.degree(),
                                        school.period()))
                                .collect(Collectors.joining("\n")))
                        .param("skills", String.join(", ", dossier.skills()))
                        .param("languages", String.join(", ", dossier.languages()))
                        .param("today", LocalDate.now(ZoneOffset.UTC).toString()));
            } catch (RuntimeException failed) {
                System.err.println("Baseline call failed for " + row.id() + ": " + failed);
                answered = null;
            }
            predictions.add(new EvalPrediction(row.id(),
                    answered == null ? EvalScorer.UNKNOWN : EvalScorer.canonical(answered.nationality()), null, null));
        }
        return predictions;
    }

    static List<GoldenRow> readGolden(String location) {
        try (InputStream in = location.startsWith("classpath:")
                ? NationalityEval.class.getClassLoader().getResourceAsStream(location.substring("classpath:".length()))
                : Files.newInputStream(Path.of(location))) {
            if (in == null) {
                throw new IllegalStateException("No golden set at " + location);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                    .map(line -> JSON.readValue(line, GoldenRow.class))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ChatModel vertexModel() {
        Client client = Client.builder()
                .vertexAI(true)
                .project(envOr("GOOGLE_CLOUD_PROJECT", "hak-talent-mapping"))
                .location(envOr("GOOGLE_CLOUD_LOCATION", "us-central1"))
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().model("gemini-2.5-flash").maxOutputTokens(8192).build())
                .build();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String orNotStated(String value) {
        return value == null || value.isEmpty() ? "not stated" : value;
    }

    private record BaselineAnswer(String nationality) {}
}
