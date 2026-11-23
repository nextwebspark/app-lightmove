package app.lightmove.api.core.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.BundledPrompt;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.core.llm.service.PromptRegistrySync;
import app.lightmove.api.core.llm.service.PromptSource;
import app.lightmove.api.core.llm.service.RegistryPromptSource;
import app.lightmove.api.core.reload.PlatformConfigReload;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** #1316 with the registry on: the boot sync, what serves, the reload, and the count of what answered. */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.ai.prompt-registry.enabled=true",
        // So a reload seen without recheck() can only have come through the notification.
        "lightmove.platform.config-reload-check=PT1H"
})
class PromptRegistryIntegrationTest {

    private static final String SHORTLIST = "recruiter-shortlist";

    @Autowired BundledPrompts bundled;
    @Autowired PromptSource prompts;
    @Autowired RegistryPromptSource registry;
    @Autowired PromptRegistrySync sync;
    @Autowired PromptRegistryStore store;
    @Autowired PlatformConfigReload reload;
    @Autowired TransactionTemplate transactions;
    @Autowired CandidateShortlistService shortlist;
    @Autowired StubChatModel model;
    @Autowired JdbcTemplate app;
    @Autowired @FixtureJdbc JdbcTemplate db;

    @AfterEach
    void serveTheShippedShortlistPromptAgain() {
        db.update("DELETE FROM app_lm_platform_prompt_label WHERE prompt_id = ?", SHORTLIST);
        sync.sync(bundled.all());
        registry.reload();
        model.reset();
    }

    @Test
    @DisplayName("on a fresh boot every prompt is served from the registry, with exactly its shipped text")
    void everyPromptIsServedAsShipped() {
        List<BundledPrompt> shipped = bundled.all();

        assertThat(shipped).hasSizeGreaterThanOrEqualTo(16);
        for (BundledPrompt prompt : shipped) {
            ServedPrompt served = prompts.current(prompt.promptId(), PromptKind.PROSE);
            assertThat(served.body()).as(prompt.promptId()).isEqualTo(prompt.body());
            assertThat(served.version()).as(prompt.promptId()).isNotNull();
            assertThat(served.modelId()).as(prompt.promptId()).isNull();
        }
    }

    @Test
    @DisplayName("a release with a changed prompt moves production to it, and a rollback release moves it back")
    void aChangedShippedPromptMovesProduction() {
        String prompt = "sync-" + UUID.randomUUID();

        sync.sync(List.of(shippedAs(prompt, "first text")));
        sync.sync(List.of(shippedAs(prompt, "second text")));
        assertThat(productionOf(prompt)).isEqualTo(2);

        sync.sync(List.of(shippedAs(prompt, "first text")));
        assertThat(productionOf(prompt)).isEqualTo(1);
        assertThat(versionsOf(prompt)).isEqualTo(2);
    }

    @Test
    @DisplayName("a release never takes production away from an edited version, and its new text is still recorded")
    void anEditedLiveVersionStays() {
        String prompt = "sync-" + UUID.randomUUID();
        sync.sync(List.of(shippedAs(prompt, "first text")));
        insertEdited(prompt, 2, "edited text", null, null);
        store.moveProduction(prompt, 1, 2, null, "tested");

        sync.sync(List.of(shippedAs(prompt, "second text")));

        assertThat(productionOf(prompt)).isEqualTo(2);
        assertThat(db.queryForObject("""
                SELECT origin FROM app_lm_platform_prompt_version WHERE prompt_id = ? AND version = 3
                """, String.class, prompt)).isEqualTo("BUNDLED");
    }

    @Test
    @DisplayName("another revision moving production to its own shipped text changes nothing this build serves")
    void anotherRevisionsShippedTextIsNotServedHere() {
        int shipped = productionOf(SHORTLIST);

        sync.sync(List.of(shippedAs(SHORTLIST, "Another revision's shortlist prompt.")));
        registry.reload();

        assertThat(productionOf(SHORTLIST)).isNotEqualTo(shipped);
        ServedPrompt served = prompts.current(SHORTLIST, PromptKind.PROSE);
        assertThat(served.body()).isEqualTo(bundled.bodyOf(SHORTLIST));
        assertThat(served.version()).isEqualTo(shipped);
    }

    @Test
    @DisplayName("a sync that recorded or moved something tells every instance to reload, and an idle one does not")
    void aSyncThatChangedSomethingBumpsTheReload() {
        String prompt = "sync-" + UUID.randomUUID();
        long before = reloadNumber();

        sync.sync(List.of(shippedAs(prompt, "first text")));
        assertThat(reloadNumber()).isEqualTo(before + 1);

        sync.sync(List.of(shippedAs(prompt, "first text")));
        assertThat(reloadNumber()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("a version put live serves on an instance the notification reached, with no timed check")
    void aVersionPutLiveServesOnceTheNotificationArrives() {
        int shipped = productionOf(SHORTLIST);
        prompts.current(SHORTLIST, PromptKind.PROSE);
        insertEdited(SHORTLIST, shipped + 200, "Weigh the candidate, at once.", null, null);

        transactions.executeWithoutResult(status -> {
            store.moveProduction(SHORTLIST, shipped, shipped + 200, null, "tested");
            reload.bump(RegistryPromptSource.SCOPE);
        });

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(prompts.current(SHORTLIST, PromptKind.PROSE).body())
                        .isEqualTo("Weigh the candidate, at once."));
    }

    @Test
    @DisplayName("two instances booting on the same release record its prompt once")
    void twoInstancesBootingTogetherRecordOneVersion() throws Exception {
        List<BundledPrompt> release = List.of(shippedAs("sync-" + UUID.randomUUID(), "the same text"));
        CyclicBarrier together = new CyclicBarrier(2);
        Runnable boot = () -> {
            try {
                together.await();
            } catch (Exception interrupted) {
                throw new IllegalStateException(interrupted);
            }
            sync.sync(release);
        };

        CompletableFuture.allOf(CompletableFuture.runAsync(boot), CompletableFuture.runAsync(boot)).get();

        assertThat(versionsOf(release.getFirst().promptId())).isEqualTo(1);
        assertThat(productionOf(release.getFirst().promptId())).isEqualTo(1);
    }

    @Test
    @DisplayName("a version put live serves on an instance that only re-read the version numbers, model included")
    void aVersionPutLiveServesAfterTheBackstopCheck() {
        int shipped = productionOf(SHORTLIST);
        prompts.current(SHORTLIST, PromptKind.PROSE);
        insertEdited(SHORTLIST, shipped + 100, "Weigh the candidate, briefly.", "gemini-3.8-flash",
                "{\"temperature\": 0.3}");

        transactions.executeWithoutResult(status -> {
            store.moveProduction(SHORTLIST, shipped, shipped + 100, null, "tested");
            reload.bump(RegistryPromptSource.SCOPE);
        });
        reload.recheck();

        ServedPrompt served = prompts.current(SHORTLIST, PromptKind.PROSE);
        assertThat(served.body()).isEqualTo("Weigh the candidate, briefly.");
        assertThat(served.version()).isEqualTo(shipped + 100);
        assertThat(served.modelId()).isEqualTo("gemini-3.8-flash");
        assertThat(served.settings().temperature()).isEqualTo(0.3);

        shortlist.shortlist("a brief", "a profile");
        assertThat(model.lastPrompt().getSystemMessage().getText()).isEqualTo("Weigh the candidate, briefly.");
        assertThat(model.lastPrompt().getOptions().getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(model.lastPrompt().getOptions().getTemperature()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("every call is counted against the version that answered it, in counts and tokens only")
    void everyCallIsCountedAgainstItsVersion() {
        int version = productionOf(SHORTLIST);
        Map<String, Object> before = countsOf(SHORTLIST, version);

        shortlist.shortlist("a brief", "a profile");
        shortlist.shortlist("a brief", "a profile");
        shortlist.shortlist("a brief", "a profile");

        Map<String, Object> after = countsOf(SHORTLIST, version);
        assertThat(delta(before, after, "calls")).isEqualTo(3);
        assertThat(delta(before, after, "input_tokens")).isEqualTo(3L * StubChatModel.PROMPT_TOKENS);
        assertThat(delta(before, after, "output_tokens")).isEqualTo(3L * StubChatModel.COMPLETION_TOKENS);
        assertThat(db.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'app_lm_platform_prompt_call_daily'
                """, String.class)).containsExactlyInAnyOrder("prompt_id", "version", "provider", "model_id", "day",
                "calls", "input_tokens", "output_tokens", "finish_stop", "finish_max_tokens", "finish_safety",
                "finish_other");
    }

    @Test
    @DisplayName("a count that cannot be written does not fail the call it was counting")
    void aFailedCountDoesNotFailTheCall() {
        db.execute("ALTER TABLE app_lm_platform_prompt_call_daily RENAME TO app_lm_platform_prompt_call_daily_away");
        try {
            assertThat(shortlist.shortlist("a brief", "a profile")).isNotBlank();
        } finally {
            db.execute("ALTER TABLE app_lm_platform_prompt_call_daily_away RENAME TO app_lm_platform_prompt_call_daily");
        }
    }

    @Test
    @DisplayName("an unreadable registry read inside a caller's transaction leaves that transaction usable (#1446)")
    void anUnreadableRegistryDoesNotAbortTheCallersTransaction() {
        db.execute("ALTER TABLE app_lm_platform_prompt_version RENAME TO app_lm_platform_prompt_version_away");
        try {
            registry.reload();

            Integer answeredAfterTheRead = transactions.execute(status -> {
                assertThat(prompts.current(SHORTLIST, PromptKind.PROSE).body()).isEqualTo(bundled.bodyOf(SHORTLIST));
                return app.queryForObject("SELECT 1", Integer.class);
            });

            assertThat(answeredAfterTheRead).isEqualTo(1);
        } finally {
            db.execute("ALTER TABLE app_lm_platform_prompt_version_away RENAME TO app_lm_platform_prompt_version");
        }
    }

    @Test
    @DisplayName("the application can neither rewrite nor delete a version or a recorded move")
    void versionsAndMovesAreAppendOnly() {
        String prompt = "sync-" + UUID.randomUUID();
        sync.sync(List.of(shippedAs(prompt, "first text")));

        assertThatThrownBy(() -> app.update(
                "UPDATE app_lm_platform_prompt_version SET body = 'rewritten' WHERE prompt_id = ?", prompt))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> app.update(
                "DELETE FROM app_lm_platform_prompt_version WHERE prompt_id = ?", prompt))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> app.update(
                "UPDATE app_lm_platform_prompt_label_move SET reason = 'rewritten' WHERE prompt_id = ?", prompt))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> app.update(
                "DELETE FROM app_lm_platform_prompt_label_move WHERE prompt_id = ?", prompt))
                .hasStackTraceContaining("append-only");
    }

    private static BundledPrompt shippedAs(String promptId, String body) {
        return new BundledPrompt(promptId, body, BundledPrompts.sha256Of(body));
    }

    private void insertEdited(String promptId, int version, String body, String modelId, String settings) {
        db.update("""
                INSERT INTO app_lm_platform_prompt_version
                    (prompt_id, version, body, provider, model_id, settings, origin, change_note)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, 'EDITED', 'tested')
                """, promptId, version, body, modelId == null ? null : "VERTEX_GEMINI", modelId, settings);
    }

    private int productionOf(String promptId) {
        return store.productionOf(promptId).orElseThrow().version();
    }

    private long reloadNumber() {
        return db.queryForObject("""
                SELECT coalesce(max(version), 0) FROM app_lm_platform_config_version WHERE scope = ?
                """, Long.class, RegistryPromptSource.SCOPE);
    }

    private int versionsOf(String promptId) {
        return db.queryForObject("SELECT count(*) FROM app_lm_platform_prompt_version WHERE prompt_id = ?",
                Integer.class, promptId);
    }

    private Map<String, Object> countsOf(String promptId, int version) {
        return db.queryForMap("""
                SELECT coalesce(sum(calls), 0) AS calls, coalesce(sum(input_tokens), 0) AS input_tokens,
                       coalesce(sum(output_tokens), 0) AS output_tokens
                FROM app_lm_platform_prompt_call_daily WHERE prompt_id = ? AND version = ?
                """, promptId, version);
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String column) {
        return ((Number) after.get(column)).longValue() - ((Number) before.get(column)).longValue();
    }
}
