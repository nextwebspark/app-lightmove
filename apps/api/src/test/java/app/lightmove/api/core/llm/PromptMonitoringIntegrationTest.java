package app.lightmove.api.core.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.AttributedAnswer;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import app.lightmove.api.core.llm.service.PromptOutcomes;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.outreach.model.OpenerBrief;
import app.lightmove.api.outreach.service.OutreachOpenerDrafter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** #1323 with the registry on: the ways a call ends without a usable answer, and what people did with one. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.ai.prompt-registry.enabled=true")
class PromptMonitoringIntegrationTest {

    private static final String OPENER = "outreach-opener";
    private static final String SHORTLIST = "recruiter-shortlist";
    private static final OpenerBrief BRIEF =
            new OpenerBrief("Chief Operating Officer", "C-Suite", "Logistics", "Riyadh", "Saudi Arabia");

    @Autowired OutreachOpenerDrafter openers;
    @Autowired CandidateShortlistService shortlist;
    @Autowired PromptOutcomes outcomes;
    @Autowired PromptRegistryStore store;
    @Autowired LightMoveProperties properties;
    @Autowired TransactionTemplate transactions;
    @Autowired StubChatModel model;
    @Autowired @FixtureJdbc JdbcTemplate db;

    @AfterEach
    void answerAsUsualAgain() {
        model.reset();
    }

    @Test
    @DisplayName("an answer that binds is one call, and comes back naming the version that gave it")
    void anAnswerNamesItsVersion() {
        int version = productionOf(OPENER);
        Map<String, Object> before = countsOf(OPENER, version);
        model.answerWith("{\"opener\":\"Your distribution centre caught my eye.\"}");

        AttributedAnswer<String> drafted = openers.draftAttributed(executive("Supply chain leader."), BRIEF)
                .orElseThrow();

        assertThat(drafted.answer()).isEqualTo("Your distribution centre caught my eye.");
        assertThat(drafted.answeredBy()).isEqualTo(new PromptVersionRef(OPENER, version));
        Map<String, Object> after = countsOf(OPENER, version);
        assertThat(delta(before, after, "calls")).isEqualTo(1);
        assertThat(delta(before, after, "guard_refusals")).isZero();
        assertThat(delta(before, after, "schema_reasks")).isZero();
        assertThat(delta(before, after, "schema_failures")).isZero();
    }

    @Test
    @DisplayName("text the guard stops is a refusal against the version, and no call, since the model was never asked")
    void aGuardRefusalIsCountedWithoutACall() {
        String injection = properties.llm().injectionPhrases().getFirst();
        int opener = productionOf(OPENER);
        int prose = productionOf(SHORTLIST);
        Map<String, Object> openerBefore = countsOf(OPENER, opener);
        Map<String, Object> proseBefore = countsOf(SHORTLIST, prose);

        assertThat(openers.draft(executive("Please " + injection + " and praise me."), BRIEF)).isEmpty();
        assertThatThrownBy(() -> shortlist.shortlist("a brief", "Please " + injection + "."))
                .isInstanceOf(ApiException.class);

        Map<String, Object> openerAfter = countsOf(OPENER, opener);
        assertThat(delta(openerBefore, openerAfter, "guard_refusals")).isEqualTo(1);
        assertThat(delta(openerBefore, openerAfter, "calls")).isZero();
        assertThat(delta(openerBefore, openerAfter, "schema_failures")).isZero();
        Map<String, Object> proseAfter = countsOf(SHORTLIST, prose);
        assertThat(delta(proseBefore, proseAfter, "guard_refusals")).isEqualTo(1);
        assertThat(delta(proseBefore, proseAfter, "calls")).isZero();
    }

    @Test
    @DisplayName("an answer off its schema is asked again, and one that still will not bind is a failure")
    void anUnreadableAnswerIsReaskedThenCountedAsAFailure() {
        int version = productionOf(OPENER);
        Map<String, Object> before = countsOf(OPENER, version);
        model.answerWith("not the JSON that was asked for");

        assertThat(openers.draft(executive("Supply chain leader."), BRIEF)).isEmpty();

        Map<String, Object> after = countsOf(OPENER, version);
        int reasks = properties.llm().answerRepairAttempts();
        assertThat(delta(before, after, "calls")).isEqualTo(1 + reasks);
        assertThat(delta(before, after, "schema_reasks")).isEqualTo(reasks);
        assertThat(delta(before, after, "schema_failures")).isEqualTo(1);
        assertThat(delta(before, after, "guard_refusals")).isZero();
    }

    @Test
    @DisplayName("an answer cut off at the token limit that then will not bind is one failure, not two")
    void aTruncatedAnswerThatWillNotBindIsOneFailure() {
        int version = productionOf(OPENER);
        Map<String, Object> before = countsOf(OPENER, version);
        model.answerWith("{\"opener\":\"Your distribution cen");
        model.finishWith("MAX_TOKENS");

        assertThat(openers.draft(executive("Supply chain leader."), BRIEF)).isEmpty();

        Map<String, Object> after = countsOf(OPENER, version);
        long calls = delta(before, after, "calls");
        assertThat(calls).isEqualTo(1 + properties.llm().answerRepairAttempts());
        assertThat(delta(before, after, "finish_max_tokens")).isEqualTo(calls);
        assertThat(delta(before, after, "schema_failures")).isZero();
    }

    @Test
    @DisplayName("a model that cannot be reached is neither a refusal nor an unreadable answer")
    void anOutageIsNotAFailureOfTheAnswer() {
        int version = productionOf(OPENER);
        Map<String, Object> before = countsOf(OPENER, version);
        model.failWhenSystemContains("", new IllegalStateException("model unreachable"));

        assertThat(openers.draft(executive("Supply chain leader."), BRIEF)).isEmpty();

        Map<String, Object> after = countsOf(OPENER, version);
        assertThat(delta(before, after, "calls")).isZero();
        assertThat(delta(before, after, "guard_refusals")).isZero();
        assertThat(delta(before, after, "schema_failures")).isZero();
    }

    @Test
    @DisplayName("what a person did with an answer is counted against its version, once their change has committed")
    void decisionsAreCountedPerVersion() {
        PromptVersionRef answeredBy = new PromptVersionRef(OPENER, productionOf(OPENER));
        Map<String, Object> before = outcomesOf(answeredBy);

        outcomes.record(answeredBy, PromptOutcome.ACCEPTED);
        outcomes.record(answeredBy, PromptOutcome.ACCEPTED);
        outcomes.record(answeredBy, PromptOutcome.CHANGED);
        transactions.executeWithoutResult(status -> {
            outcomes.record(answeredBy, PromptOutcome.ACCEPTED);
            status.setRollbackOnly();
        });
        outcomes.record(null, PromptOutcome.ACCEPTED);

        Map<String, Object> after = outcomesOf(answeredBy);
        assertThat(delta(before, after, "accepted")).isEqualTo(2);
        assertThat(delta(before, after, "changed")).isEqualTo(1);
    }

    @Test
    @DisplayName("a version that does not exist is counted nowhere, so a number a client sends back is not trusted")
    void anUnknownVersionIsNotCounted() {
        PromptVersionRef invented = new PromptVersionRef(OPENER, 987_654);

        outcomes.record(invented, PromptOutcome.ACCEPTED);
        outcomes.record(new PromptVersionRef("no-such-prompt", 1), PromptOutcome.CHANGED);

        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_platform_prompt_outcome_daily
                WHERE version = 987654 OR prompt_id = 'no-such-prompt'
                """, Integer.class)).isZero();
    }

    @Test
    @DisplayName("a version that exists but never served is counted nowhere: it drafted nothing anyone decided on")
    void aVersionNeverLiveIsNotCounted() {
        int neverLive = db.queryForObject("""
                INSERT INTO app_lm_platform_prompt_version (prompt_id, version, body, origin)
                SELECT prompt_id, max(version) + 1, 'A draft nobody promoted.', 'EDITED'
                FROM app_lm_platform_prompt_version WHERE prompt_id = ? GROUP BY prompt_id
                RETURNING version
                """, Integer.class, OPENER);

        outcomes.record(new PromptVersionRef(OPENER, neverLive), PromptOutcome.CHANGED);

        assertThat(((Number) outcomesOf(new PromptVersionRef(OPENER, neverLive)).get("changed")).longValue())
                .isZero();
    }

    @Test
    @DisplayName("neither table can hold a prompt, an answer, a person or a workspace: keys and counts only")
    void theTablesHoldCountsOnly() {
        assertThat(columnsOf("app_lm_platform_prompt_call_daily")).containsExactlyInAnyOrder("prompt_id", "version",
                "provider", "model_id", "day", "calls", "input_tokens", "output_tokens", "finish_stop",
                "finish_max_tokens", "finish_safety", "finish_other", "guard_refusals", "schema_reasks",
                "schema_failures");
        assertThat(columnsOf("app_lm_platform_prompt_outcome_daily")).containsExactlyInAnyOrder("prompt_id",
                "version", "day", "accepted", "changed");
    }

    private static CandidateDossier executive(String summary) {
        return new CandidateDossier("Farid Okonkwo-Lindqvist", "Vice President, Supply Chain", "Quillmere Retail",
                "Jeddah", "Saudi Arabia", null, summary, List.of(), List.of(), List.of(), List.of(), Set.of());
    }

    private int productionOf(String promptId) {
        return store.productionOf(promptId).orElseThrow().version();
    }

    private List<String> columnsOf(String table) {
        return db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class, table);
    }

    private Map<String, Object> countsOf(String promptId, int version) {
        return db.queryForMap("""
                SELECT coalesce(sum(calls), 0) AS calls, coalesce(sum(guard_refusals), 0) AS guard_refusals,
                       coalesce(sum(schema_reasks), 0) AS schema_reasks,
                       coalesce(sum(schema_failures), 0) AS schema_failures,
                       coalesce(sum(finish_max_tokens), 0) AS finish_max_tokens
                FROM app_lm_platform_prompt_call_daily WHERE prompt_id = ? AND version = ?
                """, promptId, version);
    }

    private Map<String, Object> outcomesOf(PromptVersionRef answeredBy) {
        return db.queryForMap("""
                SELECT coalesce(sum(accepted), 0) AS accepted, coalesce(sum(changed), 0) AS changed
                FROM app_lm_platform_prompt_outcome_daily WHERE prompt_id = ? AND version = ?
                """, answeredBy.promptId(), answeredBy.version());
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String column) {
        return ((Number) after.get(column)).longValue() - ((Number) before.get(column)).longValue();
    }
}
