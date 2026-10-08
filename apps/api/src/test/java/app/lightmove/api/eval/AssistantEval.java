package app.lightmove.api.eval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import com.google.genai.Client;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A researcher's questions asked of the real assistant over a seeded universe — what it loaded, which tools
 * it ran, what it suggested and how long it took — so a change to the prompt, a playbook or a tool is
 * measured rather than guessed. Excluded from {@code ./mvnw test}; run it with Application Default
 * Credentials:
 *
 * <pre>
 * ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=AssistantEval
 * </pre>
 *
 * <p>It reads only the HTTP answers, the saved turns and the audit trail, so the same file runs on an
 * older build for a before number. Vendor lookups are the test doubles', so a name the seeded universe
 * lacks comes back unverified. Nothing is asserted: the report is appended to
 * {@code docs/eval/assistant-eval.md}.
 */
@Tag("eval")
@IntegrationTest
@Import(AssistantEval.VertexModel.class)
class AssistantEval extends FlowTestSupport {

    private static final String CASES = "eval/assistant-cases.json";
    private static final Path REPORT = Path.of("..", "..", "docs", "eval", "assistant-eval.md");
    private static final Pattern COMPLETE_DONE_EVENT = Pattern.compile("event:done\\ndata:(.+)\\n\\n");
    private static final Duration ANSWER_WAIT = Duration.ofSeconds(90);
    private static final int ANSWER_EXCERPT = 400;

    @Autowired
    private JdbcTemplate db;

    @Test
    void run() throws Exception {
        JsonNode cases = readCases();
        ApolloUniverse universe = new ApolloUniverse(db);
        universe.reset();
        for (JsonNode company : cases.get("universe")) {
            ApolloUniverse.Seed seed = universe.company(company.get("id").asText(), company.get("name").asText())
                    .industry(company.get("industry").asText())
                    .country(company.get("country").asText())
                    .city(company.get("city").asText())
                    .employees(company.get("employees").asInt());
            List<String> keywords = new ArrayList<>();
            company.path("keywords").forEach(keyword -> keywords.add(keyword.asText()));
            seed.keywords(keywords.toArray(String[]::new)).insert();
        }

        Map<String, List<TurnResult>> results = new LinkedHashMap<>();
        for (JsonNode conversation : cases.get("conversations")) {
            String admin = freshFirm(cases);
            String projectId = project(admin, cases.get("positionTitle").asText());
            declineUpFront(admin, projectId, cases.get("declined"));
            List<TurnResult> turns = new ArrayList<>();
            String threadId = null;
            Set<Integer> listsEarlier = new HashSet<>();
            conversation.path("listsEarlier").forEach(index -> listsEarlier.add(index.asInt()));
            int index = 0;
            for (JsonNode question : conversation.get("turns")) {
                TurnResult turn = ask(admin, projectId, threadId, question.asText(),
                        listsEarlier.contains(index) && !turns.isEmpty() ? turns.getLast().suggestedNames() : null);
                threadId = turn.threadId();
                turns.add(turn);
                index++;
            }
            results.put(conversation.get("id").asText(), turns);
        }

        String report = markdown(results, cases.get("overlaps"));
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(report);
    }

    private TurnResult ask(String admin, String projectId, String threadId, String question,
                           List<String> earlierNames) throws Exception {
        long startedAt = System.nanoTime();
        JsonNode turn;
        try {
            turn = askAndAwait(admin, projectId, threadId, question);
        } catch (RuntimeException failed) {
            return TurnResult.failed(question, threadId, failed.getClass().getSimpleName());
        }
        long ms = (System.nanoTime() - startedAt) / 1_000_000;
        String answer = turn.path("answer").asText();
        List<String> names = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        int held = 0;
        for (JsonNode company : turn.path("proposal").path("companies")) {
            names.add(company.path("companyName").asText());
            keys.add(company.hasNonNull("apolloAccountId") ? company.get("apolloAccountId").asText()
                    : company.path("companyName").asText().toLowerCase(Locale.ROOT));
            if (company.hasNonNull("stage")) {
                held++;
            }
        }
        List<String> tools = new ArrayList<>();
        turn.path("steps").forEach(step -> tools.add(step.path("label").asText()));
        Integer listed = earlierNames == null ? null
                : (int) earlierNames.stream().filter(name -> answer.toLowerCase(Locale.ROOT)
                        .contains(name.toLowerCase(Locale.ROOT))).count();
        return new TurnResult(question, turn.path("threadId").asText(), playbooksOf(turn.path("id").asText()),
                tools, names, keys, held, earlierNames == null ? null : earlierNames.size(), listed,
                answer.toLowerCase(Locale.ROOT).contains("card"), ms, null, answer);
    }

    /** {@code skills} on this build, {@code specialists} on one from before the playbooks. */
    private String playbooksOf(String turnId) {
        return db.queryForObject("""
                SELECT coalesce(metadata ->> 'skills', metadata ->> 'specialists', '') FROM app_lm_audit_event
                WHERE event_type = 'ASSISTANT_ASKED' AND metadata ->> 'turnId' = ?""", String.class, turnId);
    }

    private JsonNode askAndAwait(String token, String projectId, String threadId, String question) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("question", question);
        if (threadId != null) {
            body.put("threadId", threadId);
        }
        MvcResult stream = mvc.perform(post("/api/v1/projects/" + projectId + "/assistant/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();
        String data = Awaitility.await()
                .atMost(ANSWER_WAIT)
                .pollInterval(Duration.ofMillis(200))
                .until(() -> COMPLETE_DONE_EVENT.matcher(
                        stream.getResponse().getContentAsString(StandardCharsets.UTF_8)), Matcher::find)
                .group(1);
        return json.readTree(data);
    }

    private String freshFirm(JsonNode cases) throws Exception {
        String email = "eval-" + UUID.randomUUID() + "@" + domain;
        createWorkspace(verifiedUser("Eval Consultant", email), "Kalem Luxury Retail");
        String admin = login(email);
        mvc.perform(put("/api/v1/workspace/persona")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cases.get("persona"))))
                .andExpect(status().isOk());
        return admin;
    }

    private String project(String admin, String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Retail Division\"}"))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("clientId", clientId,
                                "positionTitle", positionTitle))))
                .andReturn()).get("id").asText();
    }

    private void declineUpFront(String admin, String projectId, JsonNode declined) throws Exception {
        List<String> ids = new ArrayList<>();
        declined.forEach(id -> ids.add(id.asText()));
        if (ids.isEmpty()) {
            return;
        }
        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/bulk")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("apolloAccountIds", ids, "status", "declined"))))
                .andExpect(status().isOk());
    }

    private static String markdown(Map<String, List<TurnResult>> results, JsonNode overlaps) {
        StringBuilder out = new StringBuilder();
        out.append("\n## ").append(LocalDate.now(ZoneOffset.UTC)).append(" — assistant eval\n\n");
        out.append("| Case | Turn | Playbooks | Steps | Suggested | Already held | Lists earlier | Says \"card\" | ms |\n");
        out.append("|---|---|---|---|---|---|---|---|---|\n");
        int turns = 0;
        int withPlaybook = 0;
        int saidCard = 0;
        int asked = 0;
        for (Map.Entry<String, List<TurnResult>> conversation : results.entrySet()) {
            for (TurnResult turn : conversation.getValue()) {
                turns++;
                withPlaybook += turn.playbooks() == null || turn.playbooks().isBlank() ? 0 : 1;
                saidCard += turn.saysCard() ? 1 : 0;
                asked += turn.steps().stream().anyMatch(step -> step.startsWith("Asking you")) ? 1 : 0;
                out.append("| ").append(conversation.getKey())
                        .append(" | ").append(turn.question())
                        .append(" | ").append(turn.failure() != null ? "**failed: " + turn.failure() + "**"
                                : blankAsDash(turn.playbooks()))
                        .append(" | ").append(String.join("; ", turn.steps()))
                        .append(" | ").append(turn.suggestedNames().size())
                        .append(" | ").append(turn.held())
                        .append(" | ").append(turn.earlierCount() == null ? "—"
                                : turn.earlierListed() + " of " + turn.earlierCount())
                        .append(" | ").append(turn.saysCard() ? "yes" : "no")
                        .append(" | ").append(turn.ms())
                        .append(" |\n");
            }
        }
        out.append("\nPlaybook loaded on ").append(withPlaybook).append(" of ").append(turns)
                .append(" turns; \"card\" said in ").append(saidCard)
                .append("; asked the consultant on ").append(asked).append(".\n");
        for (JsonNode pair : overlaps) {
            List<TurnResult> first = results.get(pair.get(0).asText());
            List<TurnResult> second = results.get(pair.get(1).asText());
            if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
                continue;
            }
            Set<String> left = new HashSet<>(first.getFirst().keys());
            Set<String> right = new HashSet<>(second.getFirst().keys());
            Set<String> shared = new HashSet<>(left);
            shared.retainAll(right);
            Set<String> either = new HashSet<>(left);
            either.addAll(right);
            out.append("\nOverlap ").append(pair.get(0).asText()).append(" ∩ ").append(pair.get(1).asText())
                    .append(": ").append(shared.size()).append(" shared of ").append(either.size()).append(".\n");
        }
        for (Map.Entry<String, List<TurnResult>> conversation : results.entrySet()) {
            for (TurnResult turn : conversation.getValue()) {
                out.append("\n- **").append(conversation.getKey()).append(" · ").append(turn.question())
                        .append("**: ").append(oneLine(turn.answer()));
                if (!turn.suggestedNames().isEmpty()) {
                    out.append(" — suggested: ").append(String.join(", ", turn.suggestedNames()));
                }
            }
        }
        return out.append("\n").toString();
    }

    private static String oneLine(String answer) {
        String flat = answer == null ? "" : answer.replaceAll("\\s+", " ").strip();
        return flat.length() <= ANSWER_EXCERPT ? flat : flat.substring(0, ANSWER_EXCERPT) + "…";
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static JsonNode readCases() throws IOException {
        try (InputStream in = AssistantEval.class.getClassLoader().getResourceAsStream(CASES)) {
            if (in == null) {
                throw new IllegalStateException("No assistant eval cases at " + CASES);
            }
            return JsonMapper.builder().build().readTree(in);
        }
    }

    private record TurnResult(String question, String threadId, String playbooks, List<String> steps,
                              List<String> suggestedNames, List<String> keys, int held, Integer earlierCount,
                              Integer earlierListed, boolean saysCard, long ms, String failure, String answer) {

        static TurnResult failed(String question, String threadId, String failure) {
            return new TurnResult(question, threadId, null, List.of(), List.of(), List.of(), 0, null, null, false, 0,
                    failure, "");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class VertexModel {

        @Bean
        @Primary
        ChatModel vertexChatModel() {
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
    }
}
