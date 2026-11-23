package app.lightmove.api.prompteval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.PromptRegistrySync;
import app.lightmove.api.core.llm.service.RegistryPromptSource;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import app.lightmove.api.prompteval.service.EvalRunWorker;
import app.lightmove.api.prompteval.service.JudgeCalibrationWorker;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * #1322: a judge compares each pair of a run's answers on a rubric's criteria, and those criteria count toward a
 * result, and so toward a put live, only once the judge agrees with a person's labels.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.ai.prompt-registry.enabled=true")
class EvalJudgeIntegrationTest extends PositionTemplateFlowSupport {

    private static final String DATASETS = "/api/v1/platform/evals/datasets";
    private static final String RUNS = "/api/v1/platform/evals/runs";
    private static final String OPENER = "outreach-opener";
    private static final String OPENER_PROMPT = "/api/v1/platform/prompts/" + OPENER;
    private static final String JUDGE_PROMPT = "/api/v1/platform/prompts/eval-judge";
    private static final String JUDGE_MARK = "You compare two answers to the same case";
    private static final String AN_OPENER = "{\"opener\":\"You moved to Kestrel Grid in September.\"}";
    private static final int PAIRS = 20;

    @Autowired StubChatModel model;
    @Autowired EvalRunWorker worker;
    @Autowired JudgeCalibrationWorker checks;
    @Autowired RegistryPromptSource registry;
    @Autowired PromptRegistrySync sync;
    @Autowired BundledPrompts bundled;

    @AfterEach
    void leaveNothingGoingAndServeTheShippedPrompts() {
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> worker.isIdle() && checks.isIdle());
        db.update("""
                UPDATE app_lm_eval_run SET status = 'FAILED', failure = 'STOPPED_UNEXPECTEDLY', finished_at = now()
                WHERE status IN ('QUEUED', 'RUNNING')
                """);
        db.update("""
                UPDATE app_lm_eval_calibration_check SET status = 'FAILED', finished_at = now()
                WHERE status IN ('QUEUED', 'RUNNING')
                """);
        db.update("DELETE FROM app_lm_platform_prompt_label WHERE prompt_id IN (?, 'eval-judge')", OPENER);
        sync.sync(bundled.all());
        registry.reload();
        model.reset();
    }

    @Test
    @DisplayName("a run on a prompt with no checks is judged pair by pair, and gives no result while its rubric "
            + "does not count")
    void aJudgedRunWithNoCountingRubricGivesNoResult() throws Exception {
        String admin = superAdmin();
        UUID dataset = openerDataset(admin, 1, 1);
        int candidate = saveVersion(admin, OPENER_PROMPT, OPENER, "MARK-CANDIDATE");
        model.answerWith(AN_OPENER);
        model.answerWhenSystemContains(JUDGE_MARK, judging("A", "tie"));

        JsonNode estimate = expect(200, postJson(admin, RUNS + "/estimate", runOf(dataset, "HOLDOUT", candidate)));
        assertThat(estimate.get("repeats").asInt()).isEqualTo(3);
        assertThat(estimate.get("calls").asInt()).as("three repeats of both versions, and a judge call a pair")
                .isEqualTo(9);
        assertThat(estimate.get("judgeCalls").asInt()).isEqualTo(3);
        assertThat(estimate.get("judgedCounts").asBoolean()).isFalse();

        JsonNode finished = awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT",
                candidate))));

        JsonNode run = finished.get("run");
        assertThat(run.get("status").asString()).isEqualTo("FINISHED");
        assertThat(run.get("verdict").isNull()).as("nothing the run measured counts").isTrue();
        assertThat(run.get("judged").asBoolean()).isTrue();
        assertThat(run.get("callsPlanned").asInt()).isEqualTo(9);
        assertThat(run.get("callsMade").asInt()).isEqualTo(9);
        JsonNode judged = finished.get("summary").get("judged");
        assertThat(judged.get("state").asString()).isEqualTo("UNLABELLED");
        assertThat(judged.get("counted").asBoolean()).isFalse();
        assertThat(judged.get("pairs").asInt()).isEqualTo(3);
        assertThat(judged.get("judgeVersion").asInt()).isPositive();
        JsonNode specific = judged.get("criteria").get(0);
        assertThat(specific.get("candidateWon").asInt() + specific.get("baselineWon").asInt()).isEqualTo(3);
        assertThat(specific.get("ties").asInt()).isZero();
        assertThat(judged.get("criteria").get(1).get("ties").asInt()).isEqualTo(3);

        List<Map<String, Object>> stored = db.queryForList("""
                SELECT criterion_key, candidate_shown_as, winner, reason FROM app_lm_eval_judgement
                WHERE run_id = ?::uuid
                """, run.get("id").asString());
        assertThat(stored).hasSize(6);
        assertThat(stored).filteredOn(row -> row.get("criterion_key").equals("specific")).allSatisfy(row -> {
            // The judge picked A every time, so the winner is whichever version it was shown as A.
            assertThat(row.get("candidate_shown_as").toString()).isIn("A", "B");
            assertThat(row.get("winner"))
                    .isEqualTo(row.get("candidate_shown_as").toString().equals("A") ? "CANDIDATE" : "BASELINE");
            assertThat(row.get("reason")).isEqualTo("A names the move.");
        });
        assertThat(judgePrompts()).hasSize(3).allSatisfy(prompt -> assertThat(prompt)
                .contains("- specific: Specific to the person").contains("ANSWER A").contains("Kestrel Grid")
                .doesNotContain("MARK-CANDIDATE"));

        JsonNode changed = getJson(admin, RUNS + "/" + run.get("id").asString() + "/cases").get("changed");
        assertThat(changed).hasSize(1);
        assertThat(changed.get(0).get("prose").get("candidate").asString()).contains("Kestrel Grid");
        assertThat(changed.get(0).get("judged")).hasSize(2);
        assertThat(changed.get(0).get("candidateShownAs")).hasSize(3);

        assertThat(gateOf(admin, OPENER_PROMPT, candidate).get("state").asString())
                .as("the pack waits for a person's review, so its run is no run at put live").isEqualTo("NONE");
        countAsReviewed(run);
        JsonNode gate = gateOf(admin, OPENER_PROMPT, candidate);
        assertThat(gate.get("state").asString()).isEqualTo("NO_RESULT");
        assertThat(gate.get("runNumber").asLong()).isEqualTo(run.get("number").asLong());
        assertThat(gate.get("datasetId").asString()).isEqualTo(dataset.toString());
        assertThat(gate.get("judged").get("counted").asBoolean()).isFalse();
        refused(400, "VALIDATION_FAILED",
                putJson(admin, OPENER_PROMPT + "/production", "{\"version\":" + candidate + "}"));
    }

    @Test
    @DisplayName("twenty labelled pairs and a check of the judge make the criteria count, until a label or the "
            + "judge changes")
    void labellingAndCheckingMakesTheCriteriaCount() throws Exception {
        String admin = superAdmin();
        UUID dataset = openerDataset(admin, 7, 1);
        String rubricUrl = DATASETS + "/" + dataset + "/rubric";
        int candidate = saveVersion(admin, OPENER_PROMPT, OPENER, "MARK-CANDIDATE");
        model.answerWith(AN_OPENER);
        model.answerWhenSystemContains(JUDGE_MARK, judging("tie", "tie"));

        assertThat(rubricOf(admin, dataset).get("pairsAvailable").asBoolean()).isFalse();
        refused(409, "EVAL_CALIBRATION_NO_PAIRS", postJson(admin, rubricUrl + "/pairs", "{}"));
        awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "DEV", candidate))));
        assertThat(rubricOf(admin, dataset).get("pairsAvailable").asBoolean()).isTrue();

        JsonNode drawn = expect(200, postJson(admin, rubricUrl + "/pairs", "{}"));
        assertThat(drawn.get("pairs")).hasSize(PAIRS);
        assertThat(drawn.get("criteria")).hasSize(2);
        JsonNode first = drawn.get("pairs").get(0);
        assertThat(first.get("split").asString()).as("held-back cases stay unseen").isEqualTo("DEV");
        assertThat(first.get("a").get("prose").asString()).contains("Kestrel Grid");
        assertThat(first.get("picks")).isEmpty();
        assertThat(first.toString()).as("a pair never says which version wrote which answer")
                .doesNotContain("CANDIDATE").doesNotContain("BASELINE");
        assertThat(expect(200, postJson(admin, rubricUrl + "/pairs", "{}")).get("pairs").get(0).get("id").asString())
                .as("opening Label pairs again draws nothing new").isEqualTo(first.get("id").asString());
        assertThat(rubricOf(admin, dataset).get("state").asString()).isEqualTo("UNLABELLED");
        refused(409, "EVAL_CALIBRATION_INCOMPLETE", postJson(admin, rubricUrl + "/check", "{}"));

        String firstPair = "/api/v1/platform/evals/rubric-pairs/" + first.get("id").asString() + "/labels";
        refused(400, "VALIDATION_FAILED", putJson(admin, firstPair, "{\"picks\":{\"specific\":\"A\"}}"));
        for (JsonNode pair : drawn.get("pairs")) {
            expect(204, putJson(admin, "/api/v1/platform/evals/rubric-pairs/" + pair.get("id").asString()
                    + "/labels", "{\"picks\":{\"specific\":\"TIE\",\"plain\":\"TIE\"}}"));
        }
        assertThat(getJson(admin, rubricUrl + "/pairs").get("pairs").get(0).get("picks").get("plain").asString())
                .isEqualTo("TIE");
        JsonNode labelled = rubricOf(admin, dataset);
        assertThat(labelled.get("state").asString()).isEqualTo("UNCHECKED");
        assertThat(labelled.get("pairsLabelled").asInt()).isEqualTo(PAIRS);
        assertThat(labelled.get("labelledBy").asString()).isEqualTo("Alok Kumar");

        expect(200, postJson(admin, rubricUrl + "/check", "{}"));
        JsonNode counting = awaitChecked(admin, dataset);
        assertThat(counting.get("state").asString()).isEqualTo("COUNTS");
        assertThat(counting.get("agreed").asInt()).isEqualTo(40);
        assertThat(counting.get("picks").asInt()).isEqualTo(40);
        assertThat(counting.get("agreementPercent").asDouble()).isEqualTo(100.0);
        assertThat(counting.get("criteria").get(0).get("agreed").asInt()).isEqualTo(PAIRS);
        assertThat(counting.get("differences")).isEmpty();
        assertThat(datasetListed(admin, dataset).get("rubric").get("state").asString()).isEqualTo("COUNTS");
        assertThat(audited("EVAL_JUDGE_CHECK_STARTED")).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(audited("EVAL_JUDGE_CHECK_FINISHED")).isEqualTo(1));

        JsonNode passed = awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT",
                candidate))));
        assertThat(passed.get("run").get("verdict").asString()).isEqualTo("PASS");
        assertThat(passed.get("summary").get("judged").get("counted").asBoolean()).isTrue();
        assertThat(passed.get("summary").get("judged").get("agreementPercent").asDouble()).isEqualTo(100.0);
        countAsReviewed(passed.get("run"));
        JsonNode gate = gateOf(admin, OPENER_PROMPT, candidate);
        assertThat(gate.get("state").asString()).isEqualTo("PASSED");
        assertThat(gate.get("judged").get("counted").asBoolean()).isTrue();
        assertThat(gate.get("judged").get("criteria").asInt()).isEqualTo(2);
        assertThat(gate.get("judged").get("forBaseline")).isEmpty();

        expect(204, putJson(admin, firstPair, "{\"picks\":{\"specific\":\"A\",\"plain\":\"TIE\"}}"));
        JsonNode relabelled = rubricOf(admin, dataset);
        assertThat(relabelled.get("state").asString()).as("a changed label needs the judge checked again")
                .isEqualTo("STALE");
        assertThat(relabelled.get("differences")).hasSize(1);
        assertThat(relabelled.get("differences").get(0).get("person").asString()).isEqualTo("A");
        assertThat(relabelled.get("differences").get(0).get("judge").asString()).isEqualTo("TIE");
        expect(200, postJson(admin, rubricUrl + "/check", "{}"));
        JsonNode rechecked = awaitChecked(admin, dataset);
        assertThat(rechecked.get("state").asString()).isEqualTo("COUNTS");
        assertThat(rechecked.get("agreed").asInt()).isEqualTo(39);

        int newJudge = saveVersion(admin, JUDGE_PROMPT, "eval-judge", "MARK-JUDGE");
        expect(200, putJson(admin, JUDGE_PROMPT + "/production",
                "{\"version\":" + newJudge + ",\"reason\":\"Tighter wording\"}"));
        JsonNode stale = rubricOf(admin, dataset);
        assertThat(stale.get("state").asString()).as("the labels were checked against the judge it replaced")
                .isEqualTo("STALE");
        assertThat(stale.get("judgeVersion").asInt()).isEqualTo(newJudge);
        assertThat(stale.get("checkedJudgeVersion").asInt()).isLessThan(newJudge);
    }

    @Test
    @DisplayName("a judge that disagrees with the person is shown and never counts")
    void aJudgeBelowTheThresholdIsShownOnly() throws Exception {
        String admin = superAdmin();
        UUID dataset = openerDataset(admin, 7, 1);
        String rubricUrl = DATASETS + "/" + dataset + "/rubric";
        int candidate = saveVersion(admin, OPENER_PROMPT, OPENER, "MARK-CANDIDATE");
        model.answerWith(AN_OPENER);
        model.answerWhenSystemContains(JUDGE_MARK, judging("tie", "tie"));
        awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "DEV", candidate))));
        for (JsonNode pair : expect(200, postJson(admin, rubricUrl + "/pairs", "{}")).get("pairs")) {
            expect(204, putJson(admin, "/api/v1/platform/evals/rubric-pairs/" + pair.get("id").asString()
                    + "/labels", "{\"picks\":{\"specific\":\"A\",\"plain\":\"TIE\"}}"));
        }

        expect(200, postJson(admin, rubricUrl + "/check", "{}"));
        JsonNode shownOnly = awaitChecked(admin, dataset);

        assertThat(shownOnly.get("state").asString()).isEqualTo("SHOWN_ONLY");
        assertThat(shownOnly.get("agreementPercent").asDouble()).isEqualTo(50.0);
        assertThat(shownOnly.get("criteria").get(0).get("agreed").asInt()).isZero();
        assertThat(shownOnly.get("differences")).hasSize(PAIRS);
        JsonNode run = awaitEnded(admin, expect(201, postJson(admin, RUNS, runOf(dataset, "HOLDOUT", candidate))));
        assertThat(run.get("run").get("verdict").isNull()).isTrue();
        assertThat(run.get("summary").get("judged").get("state").asString()).isEqualTo("SHOWN_ONLY");
    }

    @Test
    @DisplayName("nobody without the platform role is told a rubric or its pairs exist")
    void theRoutesAreGuarded() throws Exception {
        Firm firm = firm("Ordinary Firm", "admin");
        String rubric = DATASETS + "/" + UUID.randomUUID() + "/rubric";
        List<AbstractMockHttpServletRequestBuilder<?>> routes = List.of(
                get(rubric + "/pairs"), post(rubric + "/pairs"), post(rubric + "/check"),
                put("/api/v1/platform/evals/rubric-pairs/" + UUID.randomUUID() + "/labels")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"picks\":{\"specific\":\"A\"}}"));

        for (AbstractMockHttpServletRequestBuilder<?> route : routes) {
            refused(404, "NOT_FOUND",
                    mvc.perform(route.header("Authorization", bearer(firm.token()))).andReturn());
        }
    }

    /** The super admin's own dataset of openers, with a rubric of two criteria. */
    private UUID openerDataset(String admin, int dev, int holdout) throws Exception {
        UUID dataset = db.queryForObject("""
                INSERT INTO app_lm_eval_dataset (prompt_id, name, created_by)
                SELECT ?, ?, id FROM app_lm_user WHERE email = ?
                RETURNING id
                """, UUID.class, OPENER, "Test openers " + UUID.randomUUID(), "owner@" + domain);
        db.update("""
                INSERT INTO app_lm_eval_rubric (dataset_id, version, criteria)
                VALUES (?, 1, CAST(? AS jsonb))
                """, dataset, """
                [{"key":"specific","name":"Specific to the person","better":"It names something from the profile."},
                 {"key":"plain","name":"Plain and brief","better":"One or two plain sentences."}]""");
        List<String> rows = new ArrayList<>();
        for (int index = 1; index <= dev; index++) {
            rows.add(row("D-" + index, "DEV"));
        }
        for (int index = 1; index <= holdout; index++) {
            rows.add(row("H-" + index, "HOLDOUT"));
        }
        expect(200, mvc.perform(multipart(DATASETS + "/" + dataset + "/cases/import/commit")
                .file(new MockMultipartFile("file", "cases.jsonl", "application/jsonl",
                        String.join("\n", rows).getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer(admin))).andReturn());
        return dataset;
    }

    private int saveVersion(String admin, String promptUrl, String promptId, String marker) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "body", bundled.bodyOf(promptId) + "\n" + marker, "changeNote", "For the test: " + marker));
        return expect(201, postJson(admin, promptUrl + "/versions", body))
                .get("versions").get(0).get("version").asInt();
    }

    private JsonNode gateOf(String admin, String promptUrl, int version) throws Exception {
        for (JsonNode summary : getJson(admin, promptUrl).get("versions")) {
            if (summary.get("version").asInt() == version) {
                return summary.get("gate");
            }
        }
        throw new AssertionError("No version " + version + " on the prompt's page");
    }

    /** The shipped opener pack waits for a person's review; this is a run on one that has had it. */
    private void countAsReviewed(JsonNode run) {
        db.update("UPDATE app_lm_eval_run SET gate_ready = true WHERE id = ?::uuid", run.get("id").asString());
    }

    private JsonNode rubricOf(String admin, UUID dataset) throws Exception {
        return getJson(admin, DATASETS + "/" + dataset).get("rubric");
    }

    private JsonNode datasetListed(String admin, UUID dataset) throws Exception {
        for (JsonNode listed : getJson(admin, DATASETS)) {
            if (listed.get("id").asString().equals(dataset.toString())) {
                return listed;
            }
        }
        throw new AssertionError("No dataset " + dataset + " in the list");
    }

    /** What the judge was sent beside its instructions. */
    private List<String> judgePrompts() {
        return model.prompts().stream()
                .filter(prompt -> prompt.getSystemMessage().getText().contains(JUDGE_MARK))
                .map(prompt -> prompt.getUserMessage().getText())
                .toList();
    }

    private JsonNode awaitEnded(String admin, JsonNode run) {
        String url = RUNS + "/" + run.get("run").get("id").asString();
        AtomicReference<JsonNode> ended = new AtomicReference<>();
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50)).until(() -> {
            JsonNode read = getJson(admin, url);
            ended.set(read);
            return !List.of("QUEUED", "RUNNING").contains(read.get("run").get("status").asString());
        });
        assertThat(ended.get().get("run").get("status").asString()).isEqualTo("FINISHED");
        return ended.get();
    }

    private JsonNode awaitChecked(String admin, UUID dataset) {
        AtomicReference<JsonNode> checked = new AtomicReference<>();
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50)).until(() -> {
            JsonNode rubric = rubricOf(admin, dataset);
            checked.set(rubric);
            return !rubric.get("state").asString().equals("CHECKING");
        });
        assertThat(checked.get().get("lastCheckFailed").asBoolean()).isFalse();
        return checked.get();
    }

    private static String runOf(UUID dataset, String split, Integer candidateVersion) {
        return "{\"datasetId\":\"%s\",\"split\":\"%s\",\"candidateVersion\":%s}"
                .formatted(dataset, split, candidateVersion);
    }

    private static String judging(String specific, String plain) {
        return """
                {"picks":[{"criterion":"specific","better":"%s","reason":"A names the move."},\
                {"criterion":"plain","better":"%s","reason":"Both are one sentence."}]}""".formatted(specific, plain);
    }

    private static String row(String key, String split) {
        return """
                {"key":"%s","name":"A test opener","tag":"test","split":"%s","source":"SYNTHETIC",\
                "input":{"profile":{"fullName":"Tomasz Wierzbicki","title":"Chief Financial Officer",\
                "companyName":"Kestrel Grid","career":[{"company":"Kestrel Grid","title":"Chief Financial Officer",\
                "period":"September 2026 - present"}]},"role":{"title":"Chief Financial Officer",\
                "sector":"Energy"}}}""".formatted(key, split);
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
}
