package app.lightmove.api.promptregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.PromptRegistrySync;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.core.llm.service.PromptTrialRunner;
import app.lightmove.api.core.llm.service.RegistryPromptSource;
import app.lightmove.api.core.reload.PlatformConfigReload;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** #1317: the prompts a super admin reads, saves, tries, puts live and rolls back, with the registry serving. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.ai.prompt-registry.enabled=true")
class PromptManagementIntegrationTest extends PositionTemplateFlowSupport {

    private static final String PROMPTS = "/api/v1/platform/prompts";
    private static final String SHORTLIST = PROMPTS + "/recruiter-shortlist";
    private static final String ASSISTANT = PROMPTS + "/assistant-core";
    private static final String ASSESSMENT = PROMPTS + "/candidate-scorecard-draft";

    @Autowired CandidateShortlistService shortlist;
    @Autowired StubChatModel model;
    @Autowired PlatformConfigReload reload;
    @Autowired RegistryPromptSource registry;
    @Autowired PromptRegistrySync sync;
    @Autowired BundledPrompts bundled;
    @Autowired List<PromptTrial> trials;
    @Autowired PromptTrialRunner runner;
    @Autowired PromptRegistryStore store;

    @AfterEach
    void serveTheShippedPromptsAgain() {
        db.update("DELETE FROM app_lm_platform_prompt_label WHERE prompt_id IN (?, ?, ?)",
                "recruiter-shortlist", "assistant-core", "candidate-scorecard-draft");
        sync.sync(bundled.all());
        registry.reload();
        model.reset();
    }

    @Test
    @DisplayName("the list names every prompt with what serves, and a prompt's page carries its history")
    void listsThePromptsAndTheirHistory() throws Exception {
        String admin = superAdmin();

        JsonNode list = getJson(admin, PROMPTS);
        assertThat(list).hasSize(16);
        JsonNode assessment = promptIn(list, "candidate-scorecard-draft");
        assertThat(assessment.get("name").asString()).isEqualTo("Assessment draft");
        assertThat(assessment.get("origin").asString()).isEqualTo("BUNDLED");
        assertThat(assessment.get("shippedChanged").asBoolean()).isFalse();
        assertThat(assessment.get("modelId").isNull()).isTrue();

        JsonNode page = getJson(admin, ASSESSMENT);
        assertThat(page.get("versions")).isNotEmpty();
        assertThat(page.get("needs").asString()).isEqualTo("STRUCTURED_ANSWER");
        assertThat(page.get("defaults").get("temperature").asDouble()).isZero();
        assertThat(page.get("userMessageTemplate").asString()).contains("{profile}").contains("{transcripts}");
        assertThat(page.get("trial").get("name").asString()).isEqualTo("Strong direct evidence");
        assertThat(page.get("aiUse").get("title").asString()).isNotBlank();

        JsonNode assistant = getJson(admin, ASSISTANT);
        assertThat(assistant.get("variables")).extracting(JsonNode::asString).containsExactly("brief", "hiring");
        assertThat(assistant.get("needs").asString()).isEqualTo("TOOL_CALLING");
        assertThat(assistant.get("trial").isNull()).isTrue();

        assertThat(getJson(admin, "/api/v1/platform/ai-models")).isNotEmpty()
                .allSatisfy(listed -> assertThat(listed.get("temperature").get("max").asDouble()).isPositive());
    }

    @Test
    @DisplayName("a save is version N+1 and changes nothing that serves")
    void aSaveLeavesProductionAlone() throws Exception {
        String admin = superAdmin();
        int live = getJson(admin, SHORTLIST).get("prompt").get("liveVersion").asInt();

        JsonNode saved = expect(201, postJson(admin, SHORTLIST + "/versions", """
                {"body":"Weigh the candidate, briefly.","provider":"VERTEX_GEMINI","modelId":"gemini-3.8-flash",
                 "settings":{"temperature":0.3},"changeNote":"Shorter, and on the newer model"}
                """));

        JsonNode newest = saved.get("versions").get(0);
        assertThat(newest.get("origin").asString()).isEqualTo("EDITED");
        assertThat(newest.get("live").asBoolean()).isFalse();
        assertThat(newest.get("author").asString()).isEqualTo("Alok Kumar");
        assertThat(newest.get("modelName").asString()).isEqualTo("Gemini 3.8 Flash");
        assertThat(newest.get("settings").get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(saved.get("prompt").get("liveVersion").asInt()).isEqualTo(live);
        assertThat(getJson(admin, SHORTLIST + "/versions/" + newest.get("version").asInt()).get("body").asString())
                .isEqualTo("Weigh the candidate, briefly.");

        shortlist.shortlist("a brief", "a profile");
        assertThat(model.lastPrompt().getSystemMessage().getText()).isEqualTo(bundled.bodyOf("recruiter-shortlist"));
        assertThat(audited("PROMPT_VERSION_SAVED")).isEqualTo(1);
        assertThat(auditMentions("Weigh the candidate")).isZero();
    }

    @Test
    @DisplayName("a model-only change is a version of its own")
    void aModelOnlyChangeSaves() throws Exception {
        String admin = superAdmin();
        String body = json.writeValueAsString(Map.of("body", bundled.bodyOf("recruiter-shortlist"),
                "provider", "VERTEX_GEMINI", "modelId", "gemini-3.1-flash-lite", "changeNote", "Cheaper model"));

        JsonNode saved = expect(201, postJson(admin, SHORTLIST + "/versions", body));

        assertThat(saved.get("versions").get(0).get("modelId").asString()).isEqualTo("gemini-3.1-flash-lite");
    }

    @Test
    @DisplayName("an edit that drops or adds a variable is refused, naming the prompt's own")
    void aBrokenContractIsRefused() throws Exception {
        String admin = superAdmin();
        String shipped = bundled.bodyOf("assistant-core");

        JsonNode dropped = refused(400, "PROMPT_CONTRACT_BROKEN", postJson(admin, ASSISTANT + "/versions",
                save(shipped.replace("{hiring}", "the hiring context"))));
        assertThat(dropped.get("fieldErrors").get("body").asString()).contains("must keep {hiring}");

        JsonNode added = refused(400, "PROMPT_CONTRACT_BROKEN", postJson(admin, ASSISTANT + "/versions",
                save(shipped + "\nAlso read {secrets}.")));
        assertThat(added.get("fieldErrors").get("body").asString())
                .contains("{brief}").contains("{hiring}").doesNotContain("secrets");

        expect(201, postJson(admin, ASSISTANT + "/versions", save(shipped + "\nBe brief.")));
    }

    @Test
    @DisplayName("a setting outside the model's bounds, and a model a prompt cannot use, are refused by field")
    void settingsAndModelsAreHeldToTheCatalogue() throws Exception {
        String admin = superAdmin();

        JsonNode hot = refused(400, "VALIDATION_FAILED", postJson(admin, SHORTLIST + "/versions", """
                {"body":"Weigh the candidate.","settings":{"temperature":1.5},"changeNote":"Too warm"}
                """));
        assertThat(hot.get("fieldErrors").has("settings.temperature")).isTrue();

        JsonNode unknown = refused(400, "PROMPT_MODEL_UNAVAILABLE", postJson(admin, SHORTLIST + "/versions",
                """
                {"body":"Weigh the candidate.","provider":"VERTEX_GEMINI","modelId":"gemini-0-unlisted",
                 "changeNote":"A model nobody listed"}
                """));
        assertThat(unknown.get("fieldErrors").has("modelId")).isTrue();

        refused(400, "VALIDATION_FAILED", postJson(admin, SHORTLIST + "/versions", """
                {"body":"Weigh the candidate.","changeNote":" "}
                """));
    }

    @Test
    @DisplayName("putting a version live changes the next call's text and model, and a rollback restores both")
    void putLiveAndRollBack() throws Exception {
        String admin = superAdmin();
        int shipped = getJson(admin, SHORTLIST).get("prompt").get("liveVersion").asInt();
        int edited = expect(201, postJson(admin, SHORTLIST + "/versions", """
                {"body":"Weigh the candidate, briefly.","provider":"VERTEX_GEMINI","modelId":"gemini-3.8-flash",
                 "changeNote":"Shorter"}
                """)).get("versions").get(0).get("version").asInt();

        JsonNode live = expect(200, putJson(admin, SHORTLIST + "/production",
                "{\"version\":" + edited + ",\"reason\":\"Reads better on the test set\"}"));
        reload.recheck();

        assertThat(live.get("prompt").get("liveVersion").asInt()).isEqualTo(edited);
        assertThat(live.get("prompt").get("origin").asString()).isEqualTo("EDITED");
        shortlist.shortlist("a brief", "a profile");
        assertThat(model.lastPrompt().getSystemMessage().getText()).isEqualTo("Weigh the candidate, briefly.");
        assertThat(model.lastPrompt().getOptions().getModel()).isEqualTo("gemini-3.8-flash");

        refused(409, "PROMPT_ALREADY_LIVE", putJson(admin, SHORTLIST + "/production",
                "{\"version\":" + edited + ",\"reason\":\"Again\"}"));
        refused(400, "VALIDATION_FAILED", putJson(admin, SHORTLIST + "/production",
                "{\"version\":" + shipped + "}"));

        expect(200, putJson(admin, SHORTLIST + "/production",
                "{\"version\":" + shipped + ",\"reason\":\"Answers got longer\"}"));
        reload.recheck();

        shortlist.shortlist("a brief", "a profile");
        assertThat(model.lastPrompt().getSystemMessage().getText()).isEqualTo(bundled.bodyOf("recruiter-shortlist"));
        assertThat(model.lastPrompt().getOptions().getModel()).isNotEqualTo("gemini-3.8-flash");
        assertThat(audited("PROMPT_PUT_LIVE")).isEqualTo(1);
        assertThat(audited("PROMPT_ROLLED_BACK")).isEqualTo(1);
        assertThat(db.queryForObject("""
                SELECT reason FROM app_lm_platform_prompt_label_move
                WHERE prompt_id = 'recruiter-shortlist' ORDER BY id DESC LIMIT 1
                """, String.class)).isEqualTo("Answers got longer");
    }

    @Test
    @DisplayName("Try it runs the version asked for through the feature's own call path, and serves it to nobody else")
    void tryItRunsOneVersion() throws Exception {
        String admin = superAdmin();
        String shipped = bundled.bodyOf("candidate-scorecard-draft");
        int edited = expect(201, postJson(admin, ASSESSMENT + "/versions",
                save(shipped + "\nTRIED-VERSION-MARKER"))).get("versions").get(0).get("version").asInt();
        model.answerWhenSystemContains("TRIED-VERSION-MARKER", """
                {"scores":[{"id":"T1","score":8,"evidence":"Ran the 2023 refinancing herself"}],
                 "report":{"careerSummary":"A listed-company finance chief."}}
                """);

        JsonNode tried = expect(200, postJson(admin, ASSESSMENT + "/versions/" + edited + "/trial", "{}"));

        assertThat(tried.get("version").asInt()).isEqualTo(edited);
        assertThat(tried.get("rawAnswer").asString()).contains("Ran the 2023 refinancing herself");
        assertThat(tried.get("parsedAnswer").get("scores").get(0).get("score").asInt()).isEqualTo(8);
        assertThat(tried.get("inputTokens").asLong()).isEqualTo(StubChatModel.PROMPT_TOKENS);
        String sent = model.lastPrompt().getUserMessage().getText();
        assertThat(sent).contains("Layla Haddad").contains("Capital markets");
        assertThat(model.lastPrompt().getSystemMessage().getText()).endsWith("TRIED-VERSION-MARKER");
        assertThat(audited("PROMPT_TRIED")).isEqualTo(1);

        assertThat(registry.current("candidate-scorecard-draft", PromptKind.STRUCTURED).body()).isEqualTo(shipped);
        refused(409, "PROMPT_TRIAL_UNAVAILABLE", postJson(admin, ASSISTANT + "/versions/1/trial", "{}"));
    }

    @Test
    @DisplayName("every prompt answered as a document can be tried: its fictional input reaches the model")
    void everyStructuredPromptCanBeTried() {
        assertThat(trials).hasSize(14);
        for (PromptTrial trial : trials) {
            String id = trial.promptId();
            model.reset();
            int live = store.productionOf(id).orElseThrow().version();

            PromptTrialResult tried = runner.run(trial, store.version(id, live).orElseThrow());

            assertThat(tried.rawAnswer()).as(id).isNotBlank();
            assertThat(trial.userMessageTemplate()).as(id).isNotBlank();
            assertThat(model.prompts()).as(id).isNotEmpty();
            assertThat(model.prompts().getFirst().getSystemMessage().getText()).as(id).isEqualTo(bundled.bodyOf(id));
            assertThat(model.prompts().getFirst().getUserMessage().getText()).as(id)
                    .doesNotContain("@").doesNotContainPattern("[{][a-zA-Z]+[}]");
        }
    }

    @Test
    @DisplayName("Try it spends the caller's own budget, a minute at a time")
    void tryItIsBudgeted() throws Exception {
        String admin = superAdmin();
        String trial = ASSESSMENT + "/versions/" + getJson(admin, ASSESSMENT).get("prompt").get("liveVersion").asInt()
                + "/trial";
        int refusedAt = 0;
        for (int attempt = 1; attempt <= 12 && refusedAt == 0; attempt++) {
            if (postJson(admin, trial, "{}").getResponse().getStatus() == 429) {
                refusedAt = attempt;
            }
        }

        assertThat(refusedAt).isEqualTo(11);
    }

    @Test
    @DisplayName("nobody without the platform role is told any of it exists, and saving does not carry putting live")
    void theRoutesAreGuarded() throws Exception {
        Firm firm = firm("Ordinary Firm", "admin");
        List<MockHttpServletRequestBuilder> routes = List.of(
                get(PROMPTS), get(SHORTLIST), get(SHORTLIST + "/versions/1"), get("/api/v1/platform/ai-models"),
                post(SHORTLIST + "/versions").contentType(MediaType.APPLICATION_JSON).content(save("Text")),
                post(SHORTLIST + "/versions/1/trial"),
                put(SHORTLIST + "/production").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"No\"}"));
        for (MockHttpServletRequestBuilder route : routes) {
            refused(404, "NOT_FOUND",
                    mvc.perform(route.header("Authorization", bearer(firm.token()))).andReturn());
        }

        String admin = superAdmin();
        db.update("""
                DELETE FROM app_lm_role_action WHERE action_id =
                    (SELECT id FROM app_lm_action WHERE scope = 'PLATFORM' AND name = 'PROMPT_PROMOTE')
                """);
        try {
            expect(200, mvc.perform(get(PROMPTS).header("Authorization", bearer(admin))).andReturn());
            refused(404, "NOT_FOUND", putJson(admin, SHORTLIST + "/production",
                    "{\"version\":1,\"reason\":\"No\"}"));
        } finally {
            db.update("""
                    INSERT INTO app_lm_role_action (role_id, action_id)
                    SELECT r.id, a.id FROM app_lm_role r, app_lm_action a
                    WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
                      AND a.scope = 'PLATFORM' AND a.name = 'PROMPT_PROMOTE'
                    """);
        }
    }

    private String save(String body) {
        return json.writeValueAsString(Map.of("body", body, "changeNote", "Tested"));
    }

    private static JsonNode promptIn(JsonNode list, String promptId) {
        for (JsonNode prompt : list) {
            if (prompt.get("id").asString().equals(promptId)) {
                return prompt;
            }
        }
        throw new AssertionError("No prompt " + promptId + " in the list");
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
