package app.lightmove.api.screentext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Screen text from a super admin's draft to every screen's read. The tables are global and nothing rolls back, so
 * each test edits its own key and publishes or discards whatever it drafted.
 */
@IntegrationTest
class ScreenTextIntegrationTest extends PositionTemplateFlowSupport {

    private static final String PUBLIC = "/api/v1/screen-text";
    private static final String EDITOR = "/api/v1/platform/screen-text";

    @Test
    @DisplayName("a published edit reaches the public read; the default undoes it, and restoring its release redoes it")
    void publishResetAndRestore() throws Exception {
        String admin = superAdmin();
        String key = "errors-account.LAST_ADMIN";
        String defaultText = entryOf(admin, key).get("defaultText").asText();

        JsonNode draft = expect(200, putJson(admin, draftUrl(key),
                "{\"text\":\"Every workspace needs an admin. Make someone else an admin first.\"}"));
        assertThat(draft.get("draftText").asText()).startsWith("Every workspace needs an admin");
        assertThat(publicEntries().has(key)).isFalse();
        assertThat(getJson(admin, EDITOR + "/preview").at("/entries/" + key).asText())
                .startsWith("Every workspace needs an admin");

        JsonNode edited = expect(201, postJson(admin, EDITOR + "/releases", "{\"note\":\"Clearer admin wording\"}"));
        assertThat(edited.get("changedKeys").toString()).contains(key);
        assertThat(publicEntries().get(key).asText()).startsWith("Every workspace needs an admin");
        assertThat(auditCount("SCREEN_TEXT_PUBLISHED", edited.get("number").asInt())).isEqualTo(1);

        resetToDefault(admin, key, defaultText);
        assertThat(publicEntries().has(key)).isFalse();

        JsonNode restored = expect(201, postJson(admin, EDITOR + "/releases/" + edited.get("number").asInt() + "/restore", "{}"));
        assertThat(restored.get("restoredFrom").asInt()).isEqualTo(edited.get("number").asInt());
        assertThat(publicEntries().get(key).asText()).startsWith("Every workspace needs an admin");

        resetToDefault(admin, key, defaultText);
    }

    @Test
    @DisplayName("an unchanged bundle is answered 304 against its ETag, with no session at all")
    void publicReadRevalidates() throws Exception {
        MvcResult first = mvc.perform(get(PUBLIC)).andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String etag = first.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        MvcResult second = mvc.perform(get(PUBLIC).header("If-None-Match", etag)).andReturn();
        assertThat(second.getResponse().getStatus()).isEqualTo(304);
    }

    @Test
    @DisplayName("the editor is a 404 to anyone without the platform action")
    void editorIsHiddenFromEveryoneElse() throws Exception {
        Firm firm = firm("Ordinary Firm", "maya");
        expectRefused(404, "NOT_FOUND", mvc.perform(get(EDITOR).header("Authorization", bearer(firm.token()))).andReturn());
        expectRefused(404, "NOT_FOUND", putJson(firm.token(), draftUrl("errors-account.LAST_ADMIN"), "{\"text\":\"x\"}"));
    }

    @Test
    @DisplayName("an edit that drops a placeholder, adds markup, runs long or names a vendor is refused under the field")
    void unsafeEditsAreRefused() throws Exception {
        String admin = superAdmin();
        String unexpected = draftUrl("errors-general.unexpected");

        assertFieldRefused(putJson(admin, unexpected, "{\"text\":\"Something broke. Contact support.\"}"));
        assertFieldRefused(putJson(admin, unexpected, "{\"text\":\"<b>Broken</b> {reference}\"}"));
        assertFieldRefused(putJson(admin, unexpected, "{\"text\":\"" + "a".repeat(400) + " {reference}\"}"));
        assertFieldRefused(putJson(admin, unexpected, "{\"text\":\"Nylas is down. Quote {reference}.\"}"));
        expectRefused(404, "SCREEN_TEXT_KEY_UNKNOWN", putJson(admin, draftUrl("errors-general.NOT_A_KEY"), "{\"text\":\"Hi\"}"));
    }

    @Test
    @DisplayName("a draft saved over a version somebody replaced is refused, and theirs stands")
    void staleDraftIsRefused() throws Exception {
        String admin = superAdmin();
        String url = draftUrl("errors-settings.SCREEN_TEXT_STALE");

        JsonNode first = expect(200, putJson(admin, url, "{\"text\":\"Someone else edited this. Reload to see it.\"}"));
        long version = first.get("draftVersion").asLong();
        expect(200, putJson(admin, url,
                "{\"text\":\"Someone else edited this first. Reload to see it.\",\"version\":" + version + "}"));
        expectRefused(409, "SCREEN_TEXT_STALE", putJson(admin, url,
                "{\"text\":\"Mine wins.\",\"version\":" + version + "}"));

        mvc.perform(delete(url).header("Authorization", bearer(admin))).andReturn();
    }

    @Test
    @DisplayName("publishing with nothing drafted is refused rather than writing an empty release")
    void nothingToPublish() throws Exception {
        String admin = superAdmin();
        postJson(admin, EDITOR + "/releases", "{}");
        expectRefused(409, "SCREEN_TEXT_NOTHING_TO_PUBLISH", postJson(admin, EDITOR + "/releases", "{}"));
    }

    private void assertFieldRefused(MvcResult result) throws Exception {
        JsonNode refused = expect(400, result);
        assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(refused.at("/fieldErrors/text").asText()).isNotBlank();
    }

    private JsonNode publicEntries() throws Exception {
        return body(mvc.perform(get(PUBLIC)).andReturn()).get("entries");
    }

    private void resetToDefault(String admin, String key, String defaultText) throws Exception {
        expect(200, putJson(admin, draftUrl(key), json.writeValueAsString(Map.of("text", defaultText))));
        expect(201, postJson(admin, EDITOR + "/releases", "{}"));
    }

    private JsonNode entryOf(String admin, String key) throws Exception {
        for (JsonNode entry : getJson(admin, EDITOR + "/entries?screen=" + key.substring(0, key.indexOf('.')))) {
            if (entry.get("key").asText().equals(key)) {
                return entry;
            }
        }
        throw new AssertionError("no entry " + key);
    }

    private long auditCount(String event, int releaseNumber) {
        return db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = ? AND target_type = 'screen_text_release' AND target_id = ?
                """, Long.class, event, String.valueOf(releaseNumber));
    }

    private static String draftUrl(String key) {
        return EDITOR + "/drafts/" + key;
    }
}
