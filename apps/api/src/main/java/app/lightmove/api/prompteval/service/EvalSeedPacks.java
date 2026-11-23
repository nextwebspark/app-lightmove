package app.lightmove.api.prompteval.service;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.model.EvalSeedPack;
import app.lightmove.api.prompteval.model.NewEvalCase;
import app.lightmove.api.prompteval.model.RubricCriterion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
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

    static final String NATIONALITY_ROWS = "eval/nationality-synthetic.jsonl";
    static final String ASSISTANT_CASES = "eval/assistant-cases.json";
    static final String ASSESSMENT_PACK = "eval/candidate-scorecard-draft-pack-v1.json";
    static final String ASSESSMENT_RUBRIC = "eval/candidate-scorecard-draft-rubric.json";
    static final String OPENER_PACK = "eval/outreach-opener-pack-v1.json";
    static final String OPENER_RUBRIC = "eval/outreach-opener-rubric.json";

    private static final TypeReference<List<RubricCriterion>> CRITERIA = new TypeReference<>() { };

    private static final int MAX_NAME = 200;

    private final ObjectMapper json;

    public List<EvalSeedPack> all() {
        List<JsonNode> profiles = goldenRows();
        return List.of(
                new EvalSeedPack("candidate-nationality", "Nationality: synthetic profiles",
                        "Invented profiles, one for each case of the nationality rubric.",
                        labelledWith("nationality", profiles)),
                new EvalSeedPack("candidate-ai-enrich", "Seniority: synthetic profiles",
                        "The nationality set's invented profiles, labelled with the level each holds.",
                        labelledWith("seniority", profiles)),
                new EvalSeedPack("candidate-scorecard-draft", "Assessment draft: pack v1",
                        "Invented executives with notes, calls and documents on file, each labelled with the "
                                + "band a consultant expects for every competency of its brief.",
                        casesIn(ASSESSMENT_PACK), rubricIn(ASSESSMENT_RUBRIC)),
                new EvalSeedPack("outreach-opener", "Outreach opener: pack v1",
                        "Invented profiles, each with a few facts about a role. An opener has no label: a judge "
                                + "compares the two versions' openers against the rubric.",
                        casesIn(OPENER_PACK), rubricIn(OPENER_RUBRIC)),
                new EvalSeedPack("assistant-core", "Assistant: synthetic conversations",
                        "Invented conversations about an invented retail position. Its offline suite runs them in "
                                + "a workspace built for the run, so they are listed here and cannot be run.",
                        conversations()));
    }

    /** A pack written as cases already: a JSON list of the rows an import file holds one a line. */
    private List<NewEvalCase> casesIn(String resource) {
        List<NewEvalCase> cases = new ArrayList<>();
        for (JsonNode row : json.readTree(textOf(resource))) {
            JsonNode tag = row.get("tag");
            JsonNode expected = row.get("expected");
            cases.add(new NewEvalCase(row.get("key").asString(), row.get("name").asString(),
                    tag == null || tag.isNull() ? null : tag.asString(),
                    EvalCaseSplit.valueOf(row.get("split").asString()), row.get("input"),
                    expected == null || expected.isNull() ? null : expected));
        }
        return cases;
    }

    /** The criteria a judge compares two answers to a pack's cases on. Changing the file ships a new version. */
    private List<RubricCriterion> rubricIn(String resource) {
        return json.readValue(textOf(resource), CRITERIA);
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
                    opening.length() <= MAX_NAME ? opening : opening.substring(0, MAX_NAME), null,
                    EvalCaseSplit.DEV, input, null));
        }
        return cases;
    }

    private List<JsonNode> goldenRows() {
        return textOf(NATIONALITY_ROWS).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                .map(json::readTree)
                .toList();
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
