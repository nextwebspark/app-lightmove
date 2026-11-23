package app.lightmove.api.prompteval.service;

import app.lightmove.api.enrichment.candidate.service.GoldenRows;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.model.EvalSeedPack;
import app.lightmove.api.prompteval.model.NewEvalCase;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The synthetic sets committed under {@code eval/}, as the datasets a release ships. Each file keeps the shape its
 * offline suite reads, so the suite and the console score the same rows; this turns a row into a case.
 */
@Component
@RequiredArgsConstructor
public class EvalSeedPacks {

    static final String ASSISTANT_CASES = "eval/assistant-cases.json";

    private final ObjectMapper json;

    public List<EvalSeedPack> all() {
        List<JsonNode> profiles = GoldenRows.synthetic();
        return List.of(
                new EvalSeedPack(PlatformPrompt.NATIONALITY.getPromptId(), "Nationality: synthetic profiles",
                        "Invented profiles, one for each case of the nationality rubric.",
                        labelledWith("nationality", profiles)),
                new EvalSeedPack(PlatformPrompt.PROFILE_ENRICHMENT.getPromptId(), "Seniority: synthetic profiles",
                        "The nationality set's invented profiles, labelled with the level each holds.",
                        labelledWith("seniority", profiles)),
                new EvalSeedPack(PlatformPrompt.ASSISTANT.getPromptId(), "Assistant: synthetic conversations",
                        "Invented conversations about an invented retail position. Its offline suite runs them in "
                                + "a workspace built for the run, so they are listed here and cannot be run.",
                        conversations()));
    }

    /** A golden row as a case of one prompt: its profile, and the one label that prompt is scored on. */
    private List<NewEvalCase> labelledWith(String label, List<JsonNode> rows) {
        List<NewEvalCase> cases = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode value = row.path("expected").get(label);
            if (value == null || value.isNull()) {
                continue;
            }
            ObjectNode input = json.createObjectNode();
            input.set("profile", row.get("profile"));
            ObjectNode expected = json.createObjectNode();
            expected.set(label, value);
            String note = row.path("note").asString("");
            cases.add(new NewEvalCase(row.get("id").asString(), note.isBlank() ? row.get("id").asString() : note,
                    null, EvalCaseSplit.valueOf(row.path("split").asString(EvalCaseSplit.DEV.name())), input,
                    expected));
        }
        return cases;
    }

    private List<NewEvalCase> conversations() {
        List<NewEvalCase> cases = new ArrayList<>();
        for (JsonNode conversation : json.readTree(textOf(ASSISTANT_CASES)).path("conversations")) {
            ObjectNode input = json.createObjectNode();
            input.set("turns", conversation.get("turns"));
            if (conversation.has("listsEarlier")) {
                input.set("listsEarlier", conversation.get("listsEarlier"));
            }
            String opening = conversation.path("turns").path(0).asString("");
            cases.add(new NewEvalCase(conversation.get("id").asString(),
                    opening.length() <= EvalCaseFileReader.MAX_NAME ? opening
                            : opening.substring(0, EvalCaseFileReader.MAX_NAME), null,
                    EvalCaseSplit.DEV, input, null));
        }
        return cases;
    }

    private static String textOf(String resource) {
        try (InputStream in = EvalSeedPacks.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No eval set at " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
