package app.lightmove.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import app.lightmove.api.core.platformconfig.service.PlatformSettings;
import app.lightmove.api.core.platformconfig.service.PromptTexts;
import app.lightmove.api.core.platformconfig.service.ShippedPrompts;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Settings → Platform → Variables and Prompts: only a super admin reaches them, every write is bounded and audited,
 * and what is saved is what the running app reads next. The overrides are global and nothing rolls back, so each
 * test clears them after itself.
 */
@IntegrationTest
class PlatformConfigIntegrationTest extends PositionTemplateFlowSupport {

    private static final String SETTINGS = "/api/v1/platform/settings";
    private static final String PROMPTS = "/api/v1/platform/prompts";
    private static final String PICKS = PlatformSetting.SOURCING_PICKS_PER_COMPANY.key();
    private static final String HITS = PlatformSetting.SOURCING_HITS_PER_COMPANY.key();

    @Autowired private PlatformSettings platformSettings;
    @Autowired private PromptTexts promptTexts;
    @Autowired private ShippedPrompts shippedPrompts;

    @AfterEach
    void clearOverrides() {
        db.update("DELETE FROM app_lm_platform_setting");
        db.update("DELETE FROM app_lm_platform_prompt");
        db.update("UPDATE app_lm_platform_config_state SET version = version + 1");
    }

    @Test
    @DisplayName("a workspace admin, a member and a pure client are told no route exists, and /me offers them none")
    void nobodyButASuperAdminReachesTheRoutes() throws Exception {
        Firm firm = firm("Probe Firm", "admin");
        inviteAndAccept(firm.token(), "Mira Member", "member@" + domain, "MEMBER");
        String member = login("member@" + domain);
        String client = clientRepresentative(firm.token(), "Cara Client", "cara@" + domain);

        for (String token : List.of(firm.token(), member, client)) {
            for (MockHttpServletRequestBuilder route : everyRoute()) {
                MvcResult result = mvc.perform(route.header("Authorization", bearer(token))).andReturn();
                expectRefused(404, "NOT_FOUND", result);
            }
            assertThat(getJson(token, "/api/v1/auth/me").get("platformActions").size()).isZero();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_platform_setting", Long.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_platform_prompt", Long.class)).isZero();
    }

    @Test
    @DisplayName("every allowlisted value is listed with the deployment's own value as its default")
    void listShowsEveryValueAtItsDefault() throws Exception {
        String admin = superAdmin();

        JsonNode listed = getJson(admin, SETTINGS);

        assertThat(listed.get("settings").size()).isEqualTo(PlatformSetting.values().length);
        JsonNode picks = settingOf(listed, PICKS);
        assertThat(picks.get("value").asText()).isEqualTo("3");
        assertThat(picks.get("defaultValue").asText()).isEqualTo("3");
        assertThat(picks.get("overridden").asBoolean()).isFalse();
        assertThat(picks.get("group").asText()).isEqualTo("SOURCING");
        assertThat(listed.at("/config/currentVersion").asLong()).isEqualTo(listed.at("/config/loadedVersion").asLong());
    }

    @Test
    @DisplayName("a value inside its bounds is saved, read by the running app at once, and audited with no workspace")
    void changeIsReadByTheAppAndAudited() throws Exception {
        String admin = superAdmin();

        JsonNode saved = expect(200, putJson(admin, SETTINGS + "/" + PICKS, "{\"value\":\"5\"}"));

        assertThat(saved.get("value").asText()).isEqualTo("5");
        assertThat(saved.get("overridden").asBoolean()).isTrue();
        assertThat(saved.get("updatedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(platformSettings.intValue(PlatformSetting.SOURCING_PICKS_PER_COMPANY)).isEqualTo(5);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(db.queryForMap("""
                SELECT workspace_id, metadata ->> 'from' AS before, metadata ->> 'to' AS after
                FROM app_lm_audit_event WHERE event_type = 'PLATFORM_SETTING_CHANGED' AND target_id = ?
                """, PICKS)).containsEntry("before", "3").containsEntry("after", "5").containsEntry("workspace_id", null));
    }

    @Test
    @DisplayName("a value outside its bounds, or not a number, is refused naming the key; an unlisted key is not found")
    void outOfBoundsIsRefused() throws Exception {
        String admin = superAdmin();

        MvcResult tooMany = putJson(admin, SETTINGS + "/" + PICKS, "{\"value\":\"11\"}");
        expectRefused(400, "VALIDATION_FAILED", tooMany);
        assertThat(body(tooMany).at("/fieldErrors").get(PICKS).asText()).isEqualTo("Must be between 1 and 10");

        expectRefused(400, "VALIDATION_FAILED", putJson(admin, SETTINGS + "/" + PICKS, "{\"value\":\"three\"}"));
        expectRefused(404, "NOT_FOUND", putJson(admin, SETTINGS + "/auth.jwt.private-key", "{\"value\":\"x\"}"));
        assertThat(platformSettings.intValue(PlatformSetting.SOURCING_PICKS_PER_COMPANY)).isEqualTo(3);
    }

    @Test
    @DisplayName("people filed per company can never pass people asked, whichever of the two is changed")
    void picksNeverPassHits() throws Exception {
        String admin = superAdmin();
        expect(200, putJson(admin, SETTINGS + "/" + HITS, "{\"value\":\"4\"}"));

        MvcResult picks = putJson(admin, SETTINGS + "/" + PICKS, "{\"value\":\"5\"}");
        expectRefused(400, "VALIDATION_FAILED", picks);
        assertThat(body(picks).at("/fieldErrors").get(PICKS).asText())
                .isEqualTo("Must not be more than the people asked per company");

        expect(200, putJson(admin, SETTINGS + "/" + PICKS, "{\"value\":\"4\"}"));
        MvcResult hits = putJson(admin, SETTINGS + "/" + HITS, "{\"value\":\"3\"}");
        expectRefused(400, "VALIDATION_FAILED", hits);
        assertThat(body(hits).at("/fieldErrors").get(HITS).asText())
                .isEqualTo("Must not be fewer than the people filed per company");

        expect(200, putJson(admin, SETTINGS + "/" + HITS, "{\"value\":\"10\"}"));
        expect(200, putJson(admin, SETTINGS + "/" + PICKS, "{\"value\":\"10\"}"));
        assertThat(platformSettings.intValue(PlatformSetting.SOURCING_PICKS_PER_COMPANY)).isEqualTo(10);
    }

    @Test
    @DisplayName("Reset deletes the override, the app reads the default again, and it is audited")
    void resetReturnsToTheDefault() throws Exception {
        String admin = superAdmin();
        String key = PlatformSetting.MCP_MAX_RESULT_CHARS.key();
        expect(200, putJson(admin, SETTINGS + "/" + key, "{\"value\":\"20000\"}"));
        assertThat(platformSettings.intValue(PlatformSetting.MCP_MAX_RESULT_CHARS)).isEqualTo(20_000);

        JsonNode reset = body(mvc.perform(delete(SETTINGS + "/" + key).header("Authorization", bearer(admin)))
                .andReturn());

        assertThat(reset.get("overridden").asBoolean()).isFalse();
        assertThat(reset.get("value").asText()).isEqualTo(reset.get("defaultValue").asText());
        assertThat(platformSettings.intValue(PlatformSetting.MCP_MAX_RESULT_CHARS)).isEqualTo(90_000);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(db.queryForObject(
                "SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'PLATFORM_SETTING_RESET' AND target_id = ?",
                Long.class, key)).isEqualTo(1));
    }

    @Test
    @DisplayName("a value written by another instance is read here once the version moves")
    void anotherInstancesWriteIsRead() {
        db.update("INSERT INTO app_lm_platform_setting (setting_key, value) VALUES (?, '7')",
                PlatformSetting.TRIAL_CREDITS.key());
        db.update("UPDATE app_lm_platform_config_state SET version = version + 1");
        db.queryForList("SELECT pg_notify('lm_platform_config', '{\"version\":0}')");

        assertThat(platformSettings.longValue(PlatformSetting.TRIAL_CREDITS)).isEqualTo(7);
    }

    @Test
    @DisplayName("Refresh raises the version every instance compares against, and is audited")
    void refreshRaisesTheVersion() throws Exception {
        String admin = superAdmin();
        long before = getJson(admin, SETTINGS).at("/config/currentVersion").asLong();

        JsonNode refreshed = expect(200, postJson(admin, SETTINGS + "/refresh", ""));
        JsonNode viaPrompts = expect(200, postJson(admin, PROMPTS + "/refresh", ""));

        assertThat(refreshed.get("currentVersion").asLong()).isEqualTo(before + 1);
        assertThat(viaPrompts.get("currentVersion").asLong()).isEqualTo(before + 2);
        assertThat(viaPrompts.get("loadedVersion").asLong()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("every shipped prompt is listed, with the assistant's two placeholders")
    void promptsAreListed() throws Exception {
        String admin = superAdmin();

        JsonNode listed = getJson(admin, PROMPTS);

        assertThat(listed.get("prompts").size()).isEqualTo(PlatformPrompt.values().length);
        JsonNode assistant = promptOf(listed, "assistant-core");
        assertThat(assistant.get("overridden").asBoolean()).isFalse();
        assertThat(assistant.get("variables").valueStream().map(JsonNode::asText)).containsExactly("brief", "hiring");
    }

    @Test
    @DisplayName("a saved prompt is the next call's, a stale save is refused, and Revert brings the shipped one back")
    void savedPromptIsUsedAndReverted() throws Exception {
        String admin = superAdmin();
        String url = PROMPTS + "/outreach-opener";

        JsonNode saved = expect(200, putJson(admin, url, saveRequest("Write one warm line.", null)));

        assertThat(saved.get("activeVersion").asInt()).isEqualTo(1);
        assertThat(saved.get("activeBody").asText()).isEqualTo("Write one warm line.");
        assertThat(promptTexts.systemText(PlatformPrompt.OUTREACH_OPENER)).isEqualTo("Write one warm line.");

        expectRefused(409, "PLATFORM_PROMPT_STALE", putJson(admin, url, saveRequest("Someone else's.", null)));
        expect(200, putJson(admin, url, saveRequest("Write two warm lines.", 1)));

        JsonNode restored = expect(200, postJson(admin, url + "/versions/1/restore", ""));
        assertThat(restored.get("activeVersion").asInt()).isEqualTo(3);
        assertThat(restored.get("versions").size()).isEqualTo(3);
        assertThat(promptTexts.systemText(PlatformPrompt.OUTREACH_OPENER)).isEqualTo("Write one warm line.");

        JsonNode reverted = body(mvc.perform(delete(url + "/override").header("Authorization", bearer(admin)))
                .andReturn());
        assertThat(reverted.get("activeVersion").isNull()).isTrue();
        assertThat(reverted.get("activeBody").isNull()).isTrue();
        assertThat(promptTexts.systemText(PlatformPrompt.OUTREACH_OPENER))
                .isEqualTo(shippedPrompts.of(PlatformPrompt.OUTREACH_OPENER));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(db.queryForList("""
                SELECT metadata::text FROM app_lm_audit_event
                WHERE event_type IN ('PLATFORM_PROMPT_SAVED', 'PLATFORM_PROMPT_RESTORED', 'PLATFORM_PROMPT_REVERTED')
                  AND target_id = 'outreach-opener'
                """, String.class)).hasSize(4).noneMatch(metadata -> metadata.contains("warm")));
    }

    @Test
    @DisplayName("a prompt that drops a placeholder, or adds one, is refused naming them, and nothing is saved")
    void placeholdersMustStayExactly() throws Exception {
        String admin = superAdmin();
        String shipped = shippedPrompts.of(PlatformPrompt.ASSISTANT_CORE);

        MvcResult dropped = putJson(admin, PROMPTS + "/assistant-core",
                saveRequest(shipped.replace("{brief}", "the brief"), null));
        expectRefused(400, "PLATFORM_PROMPT_VARIABLES_CHANGED", dropped);
        assertThat(body(dropped).get("missing").valueStream().map(JsonNode::asText)).containsExactly("brief");

        MvcResult added = putJson(admin, PROMPTS + "/call-changes", saveRequest("Use {tone} throughout.", null));
        expectRefused(400, "PLATFORM_PROMPT_VARIABLES_CHANGED", added);
        assertThat(body(added).get("added").valueStream().map(JsonNode::asText)).containsExactly("tone");

        expectRefused(400, "VALIDATION_FAILED", putJson(admin, PROMPTS + "/call-changes", saveRequest("   ", null)));
        expectRefused(404, "NOT_FOUND", putJson(admin, PROMPTS + "/no-such-prompt", saveRequest("Hello.", null)));
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_platform_prompt", Long.class)).isZero();
    }

    private List<MockHttpServletRequestBuilder> everyRoute() {
        String setting = SETTINGS + "/" + PICKS;
        String prompt = PROMPTS + "/outreach-opener";
        return List.of(
                get(SETTINGS),
                put(setting).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"4\"}"),
                delete(setting),
                post(SETTINGS + "/refresh"),
                get(PROMPTS),
                get(prompt),
                put(prompt).contentType(MediaType.APPLICATION_JSON).content(saveRequest("Hello.", null)),
                post(prompt + "/versions/1/restore"),
                delete(prompt + "/override"),
                post(PROMPTS + "/refresh"));
    }

    private String saveRequest(String body, Integer expectedVersion) {
        return json.writeValueAsString(expectedVersion == null
                ? Map.of("body", body)
                : Map.of("body", body, "expectedVersion", expectedVersion));
    }

    private static JsonNode settingOf(JsonNode listed, String key) {
        return listed.get("settings").valueStream().filter(row -> row.get("key").asText().equals(key))
                .findFirst().orElseThrow();
    }

    private static JsonNode promptOf(JsonNode listed, String id) {
        return listed.get("prompts").valueStream().filter(row -> row.get("id").asText().equals(id))
                .findFirst().orElseThrow();
    }
}
