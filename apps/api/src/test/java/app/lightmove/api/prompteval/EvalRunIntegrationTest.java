package app.lightmove.api.prompteval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.PromptRegistrySync;
import app.lightmove.api.core.llm.service.RegistryPromptSource;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import app.lightmove.api.prompteval.service.EvalRunWorker;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** #1320: a run answers the same cases with two versions and scores them, and its result gates a put live. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.ai.prompt-registry.enabled=true")
class EvalRunIntegrationTest extends PositionTemplateFlowSupport {

    private static final String DATASETS = "/api/v1/platform/evals/datasets";
    private static final String RUNS = "/api/v1/platform/evals/runs";
    private static final String NATIONALITY = "candidate-nationality";
    private static final String NATIONALITY_PROMPT = "/api/v1/platform/prompts/" + NATIONALITY;
    private static final String PROFILE = """
            {"fullName":"Greta Lindqvist","title":"Chief Risk Officer","companyName":"Saltmarsh Bank",\
            "locationCity":"Manama","locationCountry":"Bahrain","languages":["Swedish","English"]}""";
    private static final String WESTERN = answering("Western expat");
    private static final String EMIRATI = answering("Emirati");
    private static final int TOKENS_PER_CALL = StubChatModel.PROMPT_TOKENS + StubChatModel.COMPLETION_TOKENS;

    @Autowired StubChatModel model;
    @Autowired EvalRunWorker worker;
    @Autowired RegistryPromptSource registry;
    @Autowired PromptRegistrySync sync;
    @Autowired BundledPrompts bundled;

    @AfterEach
    void leaveNoRunGoingAndServeTheShippedPrompts() {
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(worker::isIdle);
        db.update("""
                UPDATE app_lm_eval_run SET status = 'FAILED', failure = 'STOPPED_UNEXPECTEDLY', finished_at = now()
                WHERE status IN ('QUEUED', 'RUNNING')
                """);
        db.update("DELETE FROM app_lm_platform_prompt_label WHERE prompt_id = ?", NATIONALITY);
        sync.sync(bundled.all());
        registry.reload();
        model.reset();
    }

    @Test
    @DisplayName("a run answers every case with both versions through the feature's own call, and stores both")
    void aRunStoresBothVersionsAnswers() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-SAME");
        model.answerWith(WESTERN);

        JsonNode estimate = expect(200, postJson(admin, RUNS + "/estimate", runOf(dataset, "HOLDOUT", candidate)));
        assertThat(estimate.get("cases").asInt()).isEqualTo(2);
        assertThat(estimate.get("repeats").asInt()).isEqualTo(1);
        assertThat(estimate.get("repeatsReason").asString()).isEqualTo("ONCE");
        assertThat(estimate.get("calls").asInt()).isEqualTo(4);
        assertThat(estimate.get("tooLarge").asBoolean()).isFalse();

        JsonNode started = expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", candidate)));
        assertThat(started.get("run").get("callsPlanned").asInt()).isEqualTo(4);
        assertThat(started.get("baseline").get("live").asBoolean()).isTrue();
        JsonNode finished = awaitEnded(admin, started);

        JsonNode run = finished.get("run");
        assertThat(run.get("status").asString()).isEqualTo("FINISHED");
        assertThat(run.get("verdict").asString()).isEqualTo("PASS");
        assertThat(run.get("callsMade").asInt()).isEqualTo(4);
        assertThat(run.get("inputTokens").asLong() + run.get("outputTokens").asLong())
                .isEqualTo(4L * TOKENS_PER_CALL);
        assertThat(run.get("startedBy").asString()).isEqualTo("Alok Kumar");
        assertThat(finished.get("countsAtPutLive").asBoolean()).isTrue();
        JsonNode group = checkNamed(finished, "nationality-group");
        assertThat(group.get("candidate").asDouble()).isEqualTo(50.0);
        assertThat(group.get("baseline").asDouble()).isEqualTo(50.0);
        assertThat(group.get("worseBeyondTolerance").asBoolean()).isFalse();

        List<Map<String, Object>> stored = db.queryForList("""
                SELECT side, answer ->> 'category' AS category, verdicts ->> 'nationality-group' AS verdict
                FROM app_lm_eval_result WHERE run_id = ?::uuid ORDER BY side
                """, run.get("id").asString());
        assertThat(stored).extracting(row -> row.get("side"))
                .containsExactly("BASELINE", "BASELINE", "CANDIDATE", "CANDIDATE");
        assertThat(stored).allSatisfy(row -> assertThat(row.get("category")).isEqualTo("Western expat"));
        assertThat(stored).extracting(row -> row.get("verdict")).containsOnly("PASSED", "FAILED");

        assertThat(promptsAboutTheCase()).hasSize(4);
        assertThat(promptsAboutTheCase().stream()
                .filter(prompt -> prompt.getSystemMessage().getText().contains("MARK-SAME"))).hasSize(2);

        JsonNode cases = getJson(admin, RUNS + "/" + run.get("id").asString() + "/cases");
        assertThat(cases.get("changed")).isEmpty();
        assertThat(cases.get("unchanged").asInt()).isEqualTo(2);
        assertThat(getJson(admin, RUNS).get(0).get("number").asLong()).isEqualTo(run.get("number").asLong());
        assertThat(audited("EVAL_RUN_STARTED")).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(audited("EVAL_RUN_FINISHED")).isEqualTo(1));
    }

    @Test
    @DisplayName("an answer off its schema is put back to the model, as the feature does, and counted in the calls")
    void theCallsGoThroughTheFeaturesRepair() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-BROKEN");
        model.answerWith(WESTERN);
        model.answerWhenSystemContains("MARK-BROKEN", "not an answer at all");

        JsonNode finished = awaitEnded(admin,
                expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", candidate))));

        assertThat(finished.get("run").get("status").asString()).isEqualTo("FINISHED");
        assertThat(finished.get("run").get("callsMade").asInt()).isGreaterThan(4);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_eval_result
                WHERE run_id = ?::uuid AND side = 'CANDIDATE' AND answer IS NULL
                  AND raw_answer = 'not an answer at all'
                """, Integer.class, finished.get("run").get("id").asString())).isEqualTo(2);
    }

    @Test
    @DisplayName("a second start while one is going is refused, and a cancel stops it with the answers it has")
    void aSecondStartIsRefusedAndACancelStops() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-CANCELLED");
        model.answerWith(WESTERN);
        String body = runOf(dataset, "HOLDOUT", candidate);
        AtomicReference<MvcResult> secondStart = new AtomicReference<>();
        AtomicReference<String> firstRun = new AtomicReference<>();
        model.whileNextPromptIsAnswered(() -> {
            try {
                secondStart.set(postJson(admin, RUNS, body));
                postJson(admin, RUNS + "/" + awaitSet(firstRun) + "/cancel", "{}");
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });

        JsonNode started = expect(201, postJson(admin, RUNS, body));
        firstRun.set(started.get("run").get("id").asString());
        JsonNode ended = awaitEnded(admin, started);

        expectRefused(409, "EVAL_RUN_IN_PROGRESS", secondStart.get());
        assertThat(ended.get("run").get("status").asString()).isEqualTo("CANCELLED");
        assertThat(ended.get("run").get("verdict").isNull()).isTrue();
        assertThat(ended.get("countsAtPutLive").asBoolean()).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_eval_result WHERE run_id = ?::uuid",
                Integer.class, firstRun.get())).isEqualTo(1);
        assertThat(audited("EVAL_RUN_CANCELLED")).isEqualTo(1);

        expect(200, postJson(admin, RUNS + "/" + firstRun.get() + "/cancel", "{}"));
        assertThat(audited("EVAL_RUN_CANCELLED")).isEqualTo(1);
        JsonNode next = awaitEnded(admin, expect(201, postJson(admin, RUNS, body)));
        assertThat(next.get("run").get("status").asString()).isEqualTo("FINISHED");
    }

    @Test
    @DisplayName("a model that never answers fails the run, which then says nothing about the version")
    void aModelThatNeverAnswersFailsTheRun() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-DOWN");
        model.answerWith(WESTERN);
        model.failWhenSystemContains("MARK-DOWN", new IllegalStateException("the model is down"));

        JsonNode ended = awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", candidate))));

        assertThat(ended.get("run").get("status").asString()).isEqualTo("FAILED");
        assertThat(ended.get("run").get("failure").asString()).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(ended.get("summary").isNull()).isTrue();
        assertThat(gateOf(admin, candidate).get("state").asString()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("a run whose worker went quiet is taken up again from the answers it had stored")
    void aQuietRunIsResumedFromItsAnswers() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-RESUMED");
        int live = getJson(admin, NATIONALITY_PROMPT).get("prompt").get("liveVersion").asInt();
        model.answerWith(WESTERN);
        UUID run = db.queryForObject("""
                INSERT INTO app_lm_eval_run
                    (prompt_id, dataset_id, split, case_ids, candidate_version, baseline_version, repeats, status,
                     gate_ready, case_count, calls_planned, calls_made, call_cap, claim_token, claims, heartbeat_at,
                     started_at)
                SELECT ?, c.dataset_id, 'HOLDOUT', array_agg(c.id ORDER BY c.case_key), ?, ?, 1, 'RUNNING', true,
                       count(*), count(*) * 2, 1, 200, gen_random_uuid(), 1, now() - interval '1 hour',
                       now() - interval '1 hour'
                FROM app_lm_eval_case c WHERE c.dataset_id = ? AND c.split = 'HOLDOUT'
                GROUP BY c.dataset_id
                RETURNING id
                """, UUID.class, NATIONALITY, candidate, live, dataset);
        db.update("""
                INSERT INTO app_lm_eval_result (run_id, case_id, side, repeat, answer, verdicts)
                SELECT r.id, r.case_ids[1], 'BASELINE', 0, ?::jsonb,
                       '{"nationality-group":"PASSED","no-gcc-false-positive":"PASSED"}'::jsonb
                FROM app_lm_eval_run r WHERE r.id = ?
                """, WESTERN, run);

        worker.wake();
        JsonNode ended = awaitEnded(admin, getJson(admin, RUNS + "/" + run));

        assertThat(ended.get("run").get("status").asString()).isEqualTo("FINISHED");
        assertThat(promptsAboutTheCase()).hasSize(3);
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_eval_result WHERE run_id = ?", Integer.class, run))
                .isEqualTo(4);
        assertThat(ended.get("run").get("callsMade").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("a worker whose run another instance took while the model answered writes nothing more")
    void aLostClaimWritesNothing() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-TAKEN");
        model.answerWith(WESTERN);
        AtomicBoolean takenOver = new AtomicBoolean();
        model.whileNextPromptIsAnswered(() -> takenOver.set(db.update(
                "UPDATE app_lm_eval_run SET claim_token = gen_random_uuid() WHERE status = 'RUNNING'") == 1));

        String run = expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", candidate)))
                .get("run").get("id").asString();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(takenOver::get);
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(worker::isIdle);

        Map<String, Object> stored = db.queryForMap("""
                SELECT status, calls_made, input_tokens + output_tokens AS tokens, candidate_model, baseline_model
                FROM app_lm_eval_run WHERE id = ?::uuid
                """, run);
        assertThat(stored.get("status")).as("the run is the other instance's to end").isEqualTo("RUNNING");
        assertThat(((Number) stored.get("calls_made")).intValue()).isZero();
        assertThat(((Number) stored.get("tokens")).longValue()).isZero();
        assertThat(stored.get("candidate_model")).isNull();
        assertThat(stored.get("baseline_model")).isNull();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_eval_result WHERE run_id = ?::uuid",
                Integer.class, run)).isZero();
        assertThat(promptsAboutTheCase()).as("it asks nothing more either").hasSize(1);
    }

    @Test
    @DisplayName("a run claimed too often is given up on, and that ending is audited as any other")
    void aRunGivenUpOnIsAudited() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-GIVEN-UP");
        int live = getJson(admin, NATIONALITY_PROMPT).get("prompt").get("liveVersion").asInt();
        UUID run = db.queryForObject("""
                INSERT INTO app_lm_eval_run
                    (prompt_id, dataset_id, split, case_ids, candidate_version, baseline_version, repeats, status,
                     gate_ready, case_count, calls_planned, call_cap, claim_token, claims, heartbeat_at, started_at,
                     started_by)
                SELECT ?, c.dataset_id, 'HOLDOUT', array_agg(c.id ORDER BY c.case_key), ?, ?, 1, 'RUNNING', true,
                       count(*), count(*) * 2, 200, gen_random_uuid(), 3, now() - interval '1 hour',
                       now() - interval '1 hour', (SELECT id FROM app_lm_user WHERE email = ?)
                FROM app_lm_eval_case c WHERE c.dataset_id = ? AND c.split = 'HOLDOUT'
                GROUP BY c.dataset_id
                RETURNING id
                """, UUID.class, NATIONALITY, candidate, live, "owner@" + domain, dataset);

        worker.wake();
        JsonNode ended = awaitEnded(admin, getJson(admin, RUNS + "/" + run));

        assertThat(ended.get("run").get("status").asString()).isEqualTo("FAILED");
        assertThat(ended.get("run").get("failure").asString()).isEqualTo("STOPPED_UNEXPECTEDLY");
        assertThat(promptsAboutTheCase()).isEmpty();
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(audited("EVAL_RUN_FINISHED")).isEqualTo(1));
    }

    @Test
    @DisplayName("put live asks for a reason unless a holdout run found the version no worse than the one serving")
    void theRunGatesPutLive() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int shipped = getJson(admin, NATIONALITY_PROMPT).get("prompt").get("liveVersion").asInt();
        int good = saveVersion(admin, "MARK-GOOD");
        int bad = saveVersion(admin, "MARK-BAD");
        model.answerWith(WESTERN);
        model.answerWhenSystemContains("MARK-GOOD", WESTERN);
        model.answerWhenSystemContains("MARK-BAD", EMIRATI);

        assertThat(gateOf(admin, good).get("state").asString()).isEqualTo("NONE");
        JsonNode unexplained = refused(400, "VALIDATION_FAILED",
                putJson(admin, NATIONALITY_PROMPT + "/production", "{\"version\":" + good + "}"));
        assertThat(unexplained.get("fieldErrors").has("reason")).isTrue();

        awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "DEV", good))));
        assertThat(gateOf(admin, good).get("state").asString()).as("a run on the dev cases counts for nothing")
                .isEqualTo("NONE");

        JsonNode passed = awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", good))));
        assertThat(passed.get("run").get("verdict").asString()).isEqualTo("PASS");
        JsonNode gate = gateOf(admin, good);
        assertThat(gate.get("state").asString()).isEqualTo("PASSED");
        assertThat(gate.get("runNumber").asLong()).isEqualTo(passed.get("run").get("number").asLong());
        assertThat(gate.get("cases").asInt()).isEqualTo(2);
        assertThat(gate.get("checksWorse").asInt()).isZero();
        assertThat(gate.get("failedChecks")).isEmpty();

        expect(200, putJson(admin, NATIONALITY_PROMPT + "/production", "{\"version\":" + good + "}"));
        assertThat(db.queryForObject("""
                SELECT reason FROM app_lm_platform_prompt_label_move WHERE prompt_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, NATIONALITY)).isNull();
        assertThat(auditMentions("\"gate\": \"PASSED\"")).isEqualTo(1);

        JsonNode failed = awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", bad))));
        assertThat(failed.get("run").get("verdict").asString()).isEqualTo("FAIL");
        assertThat(failed.get("baseline").get("version").asInt()).isEqualTo(good);
        assertThat(checkNamed(failed, "no-gcc-false-positive").get("worseBeyondTolerance").asBoolean()).isTrue();
        JsonNode changed = getJson(admin, RUNS + "/" + failed.get("run").get("id").asString() + "/cases")
                .get("changed");
        assertThat(changed).hasSize(2);
        assertThat(changed.get(0).get("outcome").asString()).isEqualTo("REGRESSION");
        JsonNode failedGate = gateOf(admin, bad);
        assertThat(failedGate.get("state").asString()).isEqualTo("FAILED");
        assertThat(failedGate.get("failedChecks")).isNotEmpty();
        assertThat(failedGate.get("regressedCases").asInt()).isEqualTo(2);

        refused(400, "VALIDATION_FAILED",
                putJson(admin, NATIONALITY_PROMPT + "/production", "{\"version\":" + bad + ",\"reason\":\"  \"}"));
        assertThat(audited("EVAL_GATE_OVERRIDDEN")).isZero();
        expect(200, putJson(admin, NATIONALITY_PROMPT + "/production",
                "{\"version\":" + bad + ",\"reason\":\"Needed for the Gulf positions\"}"));
        assertThat(audited("EVAL_GATE_OVERRIDDEN")).isEqualTo(1);

        refused(400, "VALIDATION_FAILED",
                putJson(admin, NATIONALITY_PROMPT + "/production", "{\"version\":" + shipped + "}"));
        expect(200, putJson(admin, NATIONALITY_PROMPT + "/production",
                "{\"version\":" + shipped + ",\"reason\":\"Back to the shipped text\"}"));
    }

    @Test
    @DisplayName("a run on cases no person has reviewed is scored and shown, and counts for nothing at put live")
    void anUnreviewedDatasetsRunDoesNotCount() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int candidate = saveVersion(admin, "MARK-UNREVIEWED");
        model.answerWith(WESTERN);
        JsonNode passed = awaitEnded(admin,
                expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", candidate))));
        assertThat(gateOf(admin, candidate).get("state").asString()).isEqualTo("PASSED");

        db.update("UPDATE app_lm_eval_run SET gate_ready = false WHERE id = ?::uuid",
                passed.get("run").get("id").asString());

        assertThat(gateOf(admin, candidate).get("state").asString()).isEqualTo("NONE");
        assertThat(getJson(admin, RUNS + "/" + passed.get("run").get("id").asString())
                .get("countsAtPutLive").asBoolean()).isFalse();
        refused(400, "VALIDATION_FAILED",
                putJson(admin, NATIONALITY_PROMPT + "/production", "{\"version\":" + candidate + "}"));

        JsonNode assessment = expect(200, postJson(admin, RUNS + "/estimate",
                runOf(datasetNamed(admin, "Assessment draft: pack v1"), "HOLDOUT", newAssessmentVersion(admin))));
        assertThat(assessment.get("repeats").asInt()).isEqualTo(3);
        assertThat(assessment.get("repeatsReason").asString()).isEqualTo("PROMPT_VARIES");
        assertThat(assessment.get("calls").asInt()).as("72 answers, and the judge on each of the 36 pairs")
                .isEqualTo(108);
        assertThat(assessment.get("judgeCalls").asInt()).isEqualTo(36);
    }

    @Test
    @DisplayName("a run that could not be answered, or would cost more than one run may, is refused before it starts")
    void aRunThatCannotGoIsRefused() throws Exception {
        String admin = superAdmin();
        UUID dataset = datasetOfTwoHoldoutCases(admin);
        int live = getJson(admin, NATIONALITY_PROMPT).get("prompt").get("liveVersion").asInt();
        int candidate = saveVersion(admin, "MARK-REFUSED");

        JsonNode sameVersion = refused(400, "VALIDATION_FAILED", postJson(admin, RUNS, """
                {"datasetId":"%s","split":"HOLDOUT","candidateVersion":%d,"baselineVersion":%d}
                """.formatted(dataset, candidate, candidate)));
        assertThat(sameVersion.get("fieldErrors").has("baselineVersion")).isTrue();
        JsonNode noSuchVersion = refused(400, "VALIDATION_FAILED",
                postJson(admin, RUNS, runOf(dataset, "HOLDOUT", 9_999)));
        assertThat(noSuchVersion.get("fieldErrors").has("candidateVersion")).isTrue();
        refused(400, "EVAL_NO_CASES", postJson(admin, RUNS, runOf(newDataset(), "HOLDOUT", candidate)));
        refused(409, "EVAL_SUBJECT_UNAVAILABLE", postJson(admin, RUNS,
                runOf(datasetNamed(admin, "Assistant: synthetic conversations"), "DEV", 1)));
        refused(404, "NOT_FOUND", postJson(admin, RUNS, runOf(UUID.randomUUID(), "DEV", candidate)));

        UUID large = newDataset();
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            rows.add(row("L-" + index, "DEV", "Unknown"));
        }
        expect(200, uploadCases(admin, DATASETS + "/" + large + "/cases/import/commit", rows));
        JsonNode estimate = expect(200, postJson(admin, RUNS + "/estimate", runOf(large, "DEV", candidate)));
        assertThat(estimate.get("calls").asInt()).isEqualTo(202);
        assertThat(estimate.get("callCap").asInt()).isEqualTo(200);
        assertThat(estimate.get("tooLarge").asBoolean()).isTrue();
        assertThat(estimate.get("baselineVersion").asInt()).isEqualTo(live);
        refused(400, "EVAL_RUN_TOO_LARGE", postJson(admin, RUNS, runOf(large, "DEV", candidate)));
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_eval_run WHERE dataset_id IN (?, ?)",
                Integer.class, dataset, large)).isZero();
    }

    @Test
    @DisplayName("nobody without the platform role is told any of it exists")
    void theRoutesAreGuarded() throws Exception {
        Firm firm = firm("Ordinary Firm", "admin");
        String run = RUNS + "/" + UUID.randomUUID();
        List<AbstractMockHttpServletRequestBuilder<?>> routes = List.of(
                get(RUNS), get(run), get(run + "/cases"), post(run + "/cancel"),
                post(RUNS).contentType(MediaType.APPLICATION_JSON).content(runOf(UUID.randomUUID(), "DEV", 1)),
                post(RUNS + "/estimate").contentType(MediaType.APPLICATION_JSON)
                        .content(runOf(UUID.randomUUID(), "DEV", 1)));

        for (AbstractMockHttpServletRequestBuilder<?> route : routes) {
            refused(404, "NOT_FOUND",
                    mvc.perform(route.header("Authorization", bearer(firm.token()))).andReturn());
        }
    }

    /** Two held-back cases, one labelled Western expat and one Unknown, and one dev case. */
    private UUID datasetOfTwoHoldoutCases(String admin) throws Exception {
        UUID dataset = newDataset();
        expect(200, uploadCases(admin, DATASETS + "/" + dataset + "/cases/import/commit", List.of(
                row("H-1", "HOLDOUT", "Western expat"), row("H-2", "HOLDOUT", "Unknown"),
                row("D-1", "DEV", "Western expat"))));
        return dataset;
    }

    /** The super admin's own: a dataset with no author reads as one a release shipped, which other tests count. */
    private UUID newDataset() {
        return db.queryForObject("""
                INSERT INTO app_lm_eval_dataset (prompt_id, name, created_by)
                SELECT ?, ?, id FROM app_lm_user WHERE email = ?
                RETURNING id
                """, UUID.class, NATIONALITY, "Test set " + UUID.randomUUID(), "owner@" + domain);
    }

    private UUID datasetNamed(String admin, String name) throws Exception {
        for (JsonNode dataset : getJson(admin, DATASETS)) {
            if (dataset.get("name").asString().equals(name)) {
                return UUID.fromString(dataset.get("id").asString());
            }
        }
        throw new AssertionError("No dataset " + name + " in the list");
    }

    /** The shipped text with a marker the stub model answers by, so each version can be told what to say. */
    private int saveVersion(String admin, String marker) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "body", bundled.bodyOf(NATIONALITY) + "\n" + marker, "changeNote", "For the test: " + marker));
        return expect(201, postJson(admin, NATIONALITY_PROMPT + "/versions", body))
                .get("versions").get(0).get("version").asInt();
    }

    private int newAssessmentVersion(String admin) throws Exception {
        String prompt = "/api/v1/platform/prompts/candidate-scorecard-draft";
        String body = json.writeValueAsString(Map.of(
                "body", bundled.bodyOf("candidate-scorecard-draft") + "\nMARK-ASSESSMENT",
                "changeNote", "For the test"));
        return expect(201, postJson(admin, prompt + "/versions", body)).get("versions").get(0).get("version").asInt();
    }

    private JsonNode gateOf(String admin, int version) throws Exception {
        for (JsonNode summary : getJson(admin, NATIONALITY_PROMPT).get("versions")) {
            if (summary.get("version").asInt() == version) {
                return summary.get("gate");
            }
        }
        throw new AssertionError("No version " + version + " on the prompt's page");
    }

    /** What the model was sent as the nationality reading sends it: the profile under its own headings. */
    private List<Prompt> promptsAboutTheCase() {
        return model.prompts().stream()
                .filter(prompt -> prompt.getUserMessage().getText().contains("Name: Greta Lindqvist"))
                .toList();
    }

    private JsonNode awaitEnded(String admin, JsonNode run) {
        String url = RUNS + "/" + run.get("run").get("id").asString();
        AtomicReference<JsonNode> ended = new AtomicReference<>();
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(() -> {
            JsonNode read = getJson(admin, url);
            ended.set(read);
            return !List.of("QUEUED", "RUNNING").contains(read.get("run").get("status").asString());
        });
        return ended.get();
    }

    private static String awaitSet(AtomicReference<String> reference) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> reference.get() != null);
        return reference.get();
    }

    private static JsonNode checkNamed(JsonNode run, String id) {
        for (JsonNode check : run.get("summary").get("checks")) {
            if (check.get("id").asString().equals(id)) {
                return check;
            }
        }
        throw new AssertionError("No check " + id + " in the run's summary");
    }

    private static String runOf(UUID dataset, String split, Integer candidateVersion) {
        return "{\"datasetId\":\"%s\",\"split\":\"%s\",\"candidateVersion\":%s}"
                .formatted(dataset, split, candidateVersion);
    }

    private static String answering(String category) {
        return """
                {"category":"%s","confidence":"high","evidence_for":["Stated in the summary"],\
                "evidence_against":[],"rule_applied":"none"}""".formatted(category);
    }

    private static String row(String key, String split, String nationality) {
        return """
                {"key":"%s","name":"A test case","tag":"test","split":"%s","source":"SYNTHETIC",\
                "input":{"profile":%s},"expected":{"nationality":"%s"}}""".formatted(key, split, PROFILE,
                nationality);
    }

    private MvcResult uploadCases(String token, String url, List<String> rows) throws Exception {
        return mvc.perform(multipart(url)
                .file(new MockMultipartFile("file", "cases.jsonl", "application/jsonl",
                        String.join("\n", rows).getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer(token))).andReturn();
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
        return db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE metadata::text LIKE ? AND actor_user_id = (SELECT id FROM app_lm_user WHERE email = ?)
                """, Integer.class, "%" + text + "%", "owner@" + domain);
    }
}
