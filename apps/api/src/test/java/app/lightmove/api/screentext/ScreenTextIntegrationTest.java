package app.lightmove.api.screentext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import app.lightmove.api.screentext.constant.ScreenTextLocale;
import app.lightmove.api.screentext.service.ScreenTextBundles;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private ScreenTextBundles bundles;

    @Test
    @DisplayName("a published edit reaches the public read; the default undoes it, and restoring its release redoes it")
    void publishResetAndRestore() throws Exception {
        String admin = superAdmin();
        String key = "errors-account.LAST_ADMIN";
        String defaultText = entryOf(admin, key).get("defaultText").asText();

        try {
            JsonNode draft = expect(200, putJson(admin, draftUrl(key),
                    "{\"text\":\"Every workspace needs an admin. Make someone else an admin first.\"}"));
            assertThat(draft.get("draftText").asText()).startsWith("Every workspace needs an admin");
            JsonNode preview = getJson(admin, EDITOR + "/preview");
            assertThat(preview.at("/entries/" + key).asText()).startsWith("Every workspace needs an admin");
            assertThat(preview.get("version").asText()).isNotEqualTo(publicBundle().get("version").asText());
            assertThat(publicBundle().get("entries").has(key)).isFalse();

            JsonNode edited = expect(201, postJson(admin, EDITOR + "/releases", "{\"note\":\"Clearer admin wording\"}"));
            assertThat(edited.get("changedKeys").toString()).contains(key);
            assertThat(publicBundle().get("entries").get(key).asText()).startsWith("Every workspace needs an admin");
            assertThat(auditCount("SCREEN_TEXT_PUBLISHED", edited.get("number").asInt())).isEqualTo(1);
            int editedNumber = edited.get("number").asInt();

            resetToDefault(admin, key, defaultText);
            assertThat(publicBundle().get("entries").has(key)).isFalse();

            JsonNode restored = expect(201, postJson(admin, EDITOR + "/releases/" + editedNumber + "/restore", "{}"));
            assertThat(restored.get("restoredFrom").asInt()).isEqualTo(editedNumber);
            assertThat(restored.get("changedKeys").toString()).contains(key);
            assertThat(publicBundle().get("entries").get(key).asText()).startsWith("Every workspace needs an admin");
            assertThat(auditCount("SCREEN_TEXT_RESTORED", restored.get("number").asInt())).isEqualTo(1);

            expectRefused(409, "SCREEN_TEXT_NOTHING_TO_PUBLISH",
                    postJson(admin, EDITOR + "/releases/" + restored.get("number").asInt() + "/restore", "{}"));
        } finally {
            resetToDefault(admin, key, defaultText);
        }
    }

    @Test
    @DisplayName("an override for a key the code no longer has is never served, and the next publish drops it")
    void overridesForLostKeysAreDropped() throws Exception {
        String admin = superAdmin();
        String key = "errors-settings.SCREEN_TEXT_KEY_UNKNOWN";
        String defaultText = entryOf(admin, key).get("defaultText").asText();
        int latest = getJson(admin, EDITOR).get("latestRelease").asInt();
        db.update("""
                INSERT INTO app_lm_screen_text_release (locale, number, entries, changed_keys)
                SELECT 'en', ?, coalesce(
                    (SELECT entries FROM app_lm_screen_text_release WHERE locale = 'en' ORDER BY number DESC LIMIT 1),
                    '{}'::jsonb) || '{"errors-general.retired": "A sentence a deploy removed."}'::jsonb, '[]'::jsonb
                """, latest + 1);
        bundles.evict(ScreenTextLocale.EN);
        assertThat(publicBundle().get("entries").has("errors-general.retired")).isFalse();

        try {
            expect(200, putJson(admin, draftUrl(key), "{\"text\":\"That text is gone. Reload to see what is left.\"}"));
            JsonNode published = expect(201, postJson(admin, EDITOR + "/releases", "{}"));
            assertThat(published.get("changedKeys").toString()).contains("errors-general.retired");
            assertThat(db.queryForObject("""
                    SELECT jsonb_exists(entries, 'errors-general.retired') FROM app_lm_screen_text_release
                    WHERE locale = 'en' AND number = ?
                    """, Boolean.class, published.get("number").asInt())).isFalse();
        } finally {
            resetToDefault(admin, key, defaultText);
        }
    }

    @Test
    @DisplayName("discarding a draft a colleague has since revised is refused; discarding the current one removes it")
    void discardChecksTheVersion() throws Exception {
        String admin = superAdmin();
        String url = draftUrl("errors-settings.SCREEN_TEXT_NOTHING_TO_PUBLISH");

        long first = expect(200, putJson(admin, url, "{\"text\":\"Nothing changed, so nothing was published.\"}"))
                .get("draftVersion").asLong();
        long second = expect(200, putJson(admin, url,
                "{\"text\":\"Nothing would change, so nothing was published.\",\"version\":" + first + "}"))
                .get("draftVersion").asLong();

        expectRefused(409, "SCREEN_TEXT_STALE",
                mvc.perform(delete(url).param("version", String.valueOf(first)).header("Authorization", bearer(admin))).andReturn());
        assertThat(mvc.perform(delete(url).param("version", String.valueOf(second)).header("Authorization", bearer(admin)))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(entryOf(admin, "errors-settings.SCREEN_TEXT_NOTHING_TO_PUBLISH").get("draftText").isNull()).isTrue();
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
        expectRefused(409, "SCREEN_TEXT_NOTHING_TO_PUBLISH", postJson(admin, EDITOR + "/releases", "{}"));
    }

    private void assertFieldRefused(MvcResult result) throws Exception {
        JsonNode refused = expect(400, result);
        assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(refused.at("/fieldErrors/text").asText()).isNotBlank();
    }

    private JsonNode publicBundle() throws Exception {
        return body(mvc.perform(get(PUBLIC)).andReturn());
    }

    private void resetToDefault(String admin, String key, String defaultText) throws Exception {
        MvcResult drafted = putJson(admin, draftUrl(key), json.writeValueAsString(Map.of("text", defaultText)));
        if (drafted.getResponse().getStatus() == 200 && !body(drafted).get("draftText").isNull()) {
            expect(201, postJson(admin, EDITOR + "/releases", "{}"));
        }
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
