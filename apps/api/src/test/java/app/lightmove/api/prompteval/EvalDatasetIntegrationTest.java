package app.lightmove.api.prompteval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import app.lightmove.api.prompteval.model.EvalSeedPack;
import app.lightmove.api.prompteval.model.NewEvalCase;
import app.lightmove.api.prompteval.service.EvalDatasetSeed;
import app.lightmove.api.prompteval.service.EvalSeedPacks;
import app.lightmove.api.prompteval.service.EvalSubjects;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** #1319: the eval datasets a super admin reads, the synthetic cases imported into them, and a case's retirement. */
@IntegrationTest
class EvalDatasetIntegrationTest extends PositionTemplateFlowSupport {

    private static final String DATASETS = "/api/v1/platform/evals/datasets";
    private static final String NATIONALITY = "candidate-nationality";
    private static final String PROFILE = """
            {"fullName":"Greta Lindqvist","title":"Chief Risk Officer","companyName":"Saltmarsh Bank",\
            "locationCity":"Manama","locationCountry":"Bahrain","languages":["Swedish","English"]}""";

    @Autowired EvalSubjects subjects;
    @Autowired EvalSeedPacks packs;
    @Autowired EvalDatasetSeed seed;
    @Autowired StubChatModel model;

    @AfterEach
    void forgetTheStubbedAnswers() {
        model.reset();
    }

    @Test
    @DisplayName("the sets a release ships are listed with their split, and a case is listed without its input")
    void theShippedSetsAreListed() throws Exception {
        String admin = superAdmin();

        JsonNode nationality = datasetNamed(getJson(admin, DATASETS), "Nationality: synthetic profiles");
        assertThat(nationality.get("promptId").asString()).isEqualTo(NATIONALITY);
        assertThat(nationality.get("promptName").asString()).isEqualTo("Nationality");
        assertThat(nationality.get("cases").asInt()).isEqualTo(12);
        assertThat(nationality.get("dev").asInt()).isEqualTo(8);
        assertThat(nationality.get("holdout").asInt()).isEqualTo(4);
        assertThat(nationality.get("importable").asBoolean()).isTrue();

        JsonNode first = getJson(admin, DATASETS + "/" + nationality.get("id").asString()).get("cases").get(0);
        assertThat(first.get("key").asString()).isEqualTo("syn-01");
        assertThat(first.get("name").asString()).isEqualTo("location trap");
        assertThat(first.get("split").asString()).isEqualTo("DEV");
        assertThat(first.get("addedBy").isNull()).isTrue();
        assertThat(first.has("input")).isFalse();

        JsonNode assistant = datasetNamed(getJson(admin, DATASETS), "Assistant: synthetic conversations");
        assertThat(assistant.get("cases").asInt()).isEqualTo(16);
        assertThat(assistant.get("importable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a second boot of the same release adds nothing")
    void aSecondBootAddsNothing() throws Exception {
        String admin = superAdmin();

        seed.seed(packs.all());

        assertThat(datasetNamed(getJson(admin, DATASETS), "Seniority: synthetic profiles").get("cases").asInt())
                .isEqualTo(12);
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_eval_dataset WHERE created_by IS NULL",
                Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("every shipped case of a prompt that can be run has the shape its subject reads")
    void theShippedCasesHaveTheirSubjectsShape() {
        for (EvalSeedPack pack : packs.all()) {
            subjects.of(pack.promptId()).ifPresent(subject -> {
                for (NewEvalCase shipped : pack.cases()) {
                    assertThat(subject.problemsOf(shipped.input(), shipped.expected()))
                            .as(pack.name() + " " + shipped.key()).isEmpty();
                }
            });
        }
    }

    @Test
    @DisplayName("an import is previewed without writing, then added, and a second import of the file adds nothing")
    void anImportIsPreviewedThenAdded() throws Exception {
        String admin = superAdmin();
        String dataset = DATASETS + "/" + newDataset(NATIONALITY);
        byte[] file = casesFile(row("T-1", "DEV", "SYNTHETIC", PROFILE, "Western expat"),
                "// a comment line, and a blank one, are skipped", "",
                row("T-2", "HOLDOUT", "SYNTHETIC", PROFILE, "Unknown"));

        JsonNode preview = expect(200, uploadCases(admin, dataset + "/cases/import/preview", file));
        assertThat(preview.get("committed").asBoolean()).isFalse();
        assertThat(preview.get("rows").asInt()).isEqualTo(2);
        assertThat(preview.get("added").asInt()).isEqualTo(2);
        assertThat(preview.get("dev").asInt()).isEqualTo(1);
        assertThat(preview.get("holdout").asInt()).isEqualTo(1);
        assertThat(preview.get("problems")).isEmpty();
        assertThat(getJson(admin, dataset).get("cases")).isEmpty();

        JsonNode committed = expect(200, uploadCases(admin, dataset + "/cases/import/commit", file));
        assertThat(committed.get("committed").asBoolean()).isTrue();
        assertThat(committed.get("added").asInt()).isEqualTo(2);
        JsonNode detail = getJson(admin, dataset);
        assertThat(detail.get("dataset").get("holdout").asInt()).isEqualTo(1);
        assertThat(detail.get("cases").get(0).get("addedBy").asString()).isEqualTo("Alok Kumar");
        assertThat(detail.get("cases").get(0).get("tag").asString()).isEqualTo("test");

        JsonNode again = expect(200, uploadCases(admin, dataset + "/cases/import/commit", file));
        assertThat(again.get("added").asInt()).isZero();
        assertThat(again.get("alreadyPresent").asInt()).isEqualTo(2);
        assertThat(getJson(admin, dataset).get("cases")).hasSize(2);

        assertThat(audited("EVAL_CASES_IMPORTED")).isEqualTo(2);
        assertThat(auditMentions("Greta Lindqvist")).isZero();
    }

    @Test
    @DisplayName("a row of the wrong shape, or not marked synthetic, is named and nothing in the file is added")
    void aRowAtFaultStopsTheWholeImport() throws Exception {
        String admin = superAdmin();
        String dataset = DATASETS + "/" + newDataset(NATIONALITY);
        String withAnEmail = PROFILE.substring(0, PROFILE.length() - 1) + ",\"email\":\"greta@example.com\"}";
        byte[] file = casesFile(
                row("T-1", "DEV", "SYNTHETIC", PROFILE, "Western expat"),
                row("T-2", "DEV", "PRODUCTION", PROFILE, "Western expat"),
                row("T-3", "DEV", "SYNTHETIC", withAnEmail, "Western expat"),
                row("T-4", "DEV", "SYNTHETIC", PROFILE, "Martian"),
                row("T-5", "SOMETIMES", "SYNTHETIC", PROFILE, "Unknown"),
                row("T-1", "DEV", "SYNTHETIC", PROFILE, "Unknown"),
                "{not json",
                row("T-8", "DEV", "SYNTHETIC", "{\"title\":\"Nobody\"}", "Unknown"));

        JsonNode preview = expect(200, uploadCases(admin, dataset + "/cases/import/preview", file));

        assertThat(preview.get("added").asInt()).isEqualTo(1);
        assertThat(problemsAt(preview, 2))
                .containsExactly("source is not SYNTHETIC. Only synthetic cases can be imported.");
        assertThat(problemsAt(preview, 3)).singleElement().asString().contains("a contact, a salary or a note");
        assertThat(problemsAt(preview, 4))
                .containsExactly("expected.nationality is not a nationality group or Unknown.");
        assertThat(problemsAt(preview, 5)).containsExactly("split is not DEV or HOLDOUT.");
        assertThat(problemsAt(preview, 6)).containsExactly("key T-1 appears earlier in this file.");
        assertThat(problemsAt(preview, 7)).containsExactly("This line is not valid JSON.");
        assertThat(problemsAt(preview, 8)).containsExactly("input.profile.fullName is missing.");

        refused(400, "EVAL_IMPORT_INVALID", uploadCases(admin, dataset + "/cases/import/commit", file));
        assertThat(getJson(admin, dataset).get("cases")).isEmpty();
        assertThat(auditMentions("greta@example.com")).isZero();
    }

    @Test
    @DisplayName("a case is retired, never rewritten or removed, and its key is never given to another")
    void aCaseIsRetiredAndNeverRewritten() throws Exception {
        String admin = superAdmin();
        String dataset = DATASETS + "/" + newDataset(NATIONALITY);
        byte[] file = casesFile(row("T-1", "HOLDOUT", "SYNTHETIC", PROFILE, "Unknown"));
        expect(200, uploadCases(admin, dataset + "/cases/import/commit", file));
        String caseId = getJson(admin, dataset).get("cases").get(0).get("id").asString();
        String retire = "/api/v1/platform/evals/cases/" + caseId + "/retire";

        JsonNode retired = expect(200, postJson(admin, retire, "{}"));
        assertThat(retired.get("dataset").get("cases").asInt()).isZero();
        assertThat(retired.get("cases").get(0).get("retiredAt").isNull()).isFalse();
        expect(200, postJson(admin, retire, "{}"));
        assertThat(audited("EVAL_CASE_RETIRED")).isEqualTo(1);

        UUID id = UUID.fromString(caseId);
        assertThatThrownBy(() -> db.update("UPDATE app_lm_eval_case SET name = 'Rewritten' WHERE id = ?", id))
                .rootCause().hasMessageContaining("add-and-retire only");
        assertThatThrownBy(() -> db.update("UPDATE app_lm_eval_case SET retired_at = NULL WHERE id = ?", id))
                .rootCause().hasMessageContaining("add-and-retire only");
        assertThatThrownBy(() -> db.update("DELETE FROM app_lm_eval_case WHERE id = ?", id))
                .rootCause().hasMessageContaining("add-and-retire only");

        firm("Second Firm", "second");
        UUID someoneElse = db.queryForObject(
                "SELECT id FROM app_lm_user WHERE id <> (SELECT created_by FROM app_lm_eval_case WHERE id = ?) LIMIT 1",
                UUID.class, id);
        assertThatThrownBy(() -> db.update("UPDATE app_lm_eval_case SET created_by = ? WHERE id = ?", someoneElse, id))
                .rootCause().hasMessageContaining("add-and-retire only");
        assertThatThrownBy(() -> db.update("UPDATE app_lm_eval_case SET retired_by = ? WHERE id = ?", someoneElse, id))
                .rootCause().hasMessageContaining("add-and-retire only");
        db.update("UPDATE app_lm_eval_case SET created_by = NULL, retired_by = NULL WHERE id = ?", id);

        JsonNode again = expect(200, uploadCases(admin, dataset + "/cases/import/preview", file));
        assertThat(again.get("added").asInt()).isZero();
        assertThat(again.get("alreadyPresent").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a prompt with no eval subject takes no cases")
    void aPromptWithNoSubjectTakesNoImport() throws Exception {
        String admin = superAdmin();
        String dataset = DATASETS + "/" + newDataset("assistant-core");

        refused(409, "EVAL_SUBJECT_UNAVAILABLE", uploadCases(admin, dataset + "/cases/import/preview",
                casesFile(row("T-1", "DEV", "SYNTHETIC", PROFILE, "Unknown"))));
    }

    @Test
    @DisplayName("a stored case reaches the model through the feature's own message")
    void aCaseIsAnsweredThroughTheFeaturesOwnPath() throws Exception {
        JsonNode input = json.readTree(db.queryForObject("""
                SELECT c.input::text FROM app_lm_eval_case c JOIN app_lm_eval_dataset d ON d.id = c.dataset_id
                WHERE d.prompt_id = ? AND d.created_by IS NULL AND c.case_key = 'syn-01'
                """, String.class, NATIONALITY));
        EvalSubject subject = subjects.of(NATIONALITY).orElseThrow();

        subject.answer(input);

        assertThat(model.lastPrompt().getUserMessage().getText())
                .contains("Name: Oliver Pembridge")
                .contains("Current location (residence — not evidence of nationality): Dubai");
    }

    @Test
    @DisplayName("nobody without the platform role is told any of it exists")
    void theRoutesAreGuarded() throws Exception {
        String dataset = DATASETS + "/" + newDataset(NATIONALITY);
        Firm firm = firm("Ordinary Firm", "admin");
        MockMultipartFile file = new MockMultipartFile("file", "cases.jsonl", "application/jsonl", new byte[0]);
        List<AbstractMockHttpServletRequestBuilder<?>> routes = List.of(
                get(DATASETS), get(dataset),
                multipart(dataset + "/cases/import/preview").file(file),
                multipart(dataset + "/cases/import/commit").file(file),
                post("/api/v1/platform/evals/cases/" + UUID.randomUUID() + "/retire"));

        for (AbstractMockHttpServletRequestBuilder<?> route : routes) {
            refused(404, "NOT_FOUND",
                    mvc.perform(route.header("Authorization", bearer(firm.token()))).andReturn());
        }
    }

    private UUID newDataset(String promptId) {
        return db.queryForObject("""
                INSERT INTO app_lm_eval_dataset (prompt_id, name) VALUES (?, ?) RETURNING id
                """, UUID.class, promptId, "Test set " + UUID.randomUUID());
    }

    private static String row(String key, String split, String source, String profile, String nationality) {
        return """
                {"key":"%s","name":"A test case","tag":"test","split":"%s","source":"%s",\
                "input":{"profile":%s},"expected":{"nationality":"%s"}}""".formatted(key, split, source, profile,
                nationality);
    }

    private static byte[] casesFile(String... lines) {
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private MvcResult uploadCases(String token, String url, byte[] content) throws Exception {
        return mvc.perform(multipart(url)
                .file(new MockMultipartFile("file", "cases.jsonl", "application/jsonl", content))
                .header("Authorization", bearer(token))).andReturn();
    }

    private static List<String> problemsAt(JsonNode response, int line) {
        return response.get("problems").valueStream()
                .filter(problem -> problem.get("line").asInt() == line)
                .map(problem -> problem.get("message").asString())
                .toList();
    }

    private static JsonNode datasetNamed(JsonNode list, String name) {
        for (JsonNode dataset : list) {
            if (dataset.get("name").asString().equals(name)) {
                return dataset;
            }
        }
        throw new AssertionError("No dataset " + name + " in the list");
    }

    private JsonNode refused(int status, String code, MvcResult result) throws Exception {
        JsonNode problem = expect(status, result);
        assertThat(problem.get("code").asString()).isEqualTo(code);
        return problem;
    }

    private int audited(String eventType) {
        return db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = ? AND actor_user_id = (SELECT id FROM app_lm_user WHERE email = ?)
                """, Integer.class, eventType, "owner@" + domain);
    }

    private int auditMentions(String text) {
        return db.queryForObject("SELECT count(*) FROM app_lm_audit_event WHERE metadata::text LIKE ?",
                Integer.class, "%" + text + "%");
    }
}
