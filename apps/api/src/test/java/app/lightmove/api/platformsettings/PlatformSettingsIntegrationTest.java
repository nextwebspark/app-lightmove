package app.lightmove.api.platformsettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.config.ApplicationVariable;
import app.lightmove.api.core.config.ApplicationVariables;
import app.lightmove.api.core.reload.PlatformConfigReload;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** #1314: the application variables a super admin reads, changes and resets, and what the application then runs on. */
@IntegrationTest
class PlatformSettingsIntegrationTest extends PositionTemplateFlowSupport {

    private static final String SETTINGS = "/api/v1/platform/settings";
    private static final String COMPANIES = SETTINGS + "/enrichment.sourcing.max-companies-per-run";
    private static final String CHANGE = """
            {"value":"%s","note":"%s"}
            """;
    private static final String RESET = """
            {"note":"%s"}
            """;

    @Autowired ApplicationVariables variables;
    @Autowired PlatformConfigReload reload;
    @Autowired JdbcTemplate app;

    @AfterEach
    void serveTheConfiguredValuesAgain() {
        db.update("DELETE FROM app_lm_platform_setting");
        // TRUNCATE, because the append-only trigger refuses a DELETE even from the fixture's superuser.
        db.execute("TRUNCATE app_lm_platform_setting_change");
        variables.reload();
    }

    @Test
    @DisplayName("with nothing changed every variable is listed on its configured value")
    void listsEveryVariableOnItsDefault() throws Exception {
        JsonNode list = getJson(superAdmin(), SETTINGS);

        assertThat(list).hasSize(ApplicationVariable.values().length);
        JsonNode companies = list.get(0);
        assertThat(companies.get("key").asString()).isEqualTo("enrichment.sourcing.max-companies-per-run");
        assertThat(companies.get("group").asString()).isEqualTo("Executive sourcing");
        assertThat(companies.get("groupKeyPrefix").asString()).isEqualTo("enrichment.sourcing");
        assertThat(companies.get("label").asString()).isEqualTo("Companies per run");
        assertThat(companies.get("value").asInt()).isEqualTo(5);
        assertThat(companies.get("defaultValue").asInt()).isEqualTo(5);
        assertThat(companies.get("min").asInt()).isEqualTo(1);
        assertThat(companies.get("max").asInt()).isEqualTo(25);
        assertThat(companies.get("changed").asBoolean()).isFalse();
        assertThat(companies.get("lastChangedAt").isNull()).isTrue();
        assertThat(list.get(4).get("unit").asString()).isEqualTo("seconds");
    }

    @Test
    @DisplayName("every variable can be changed, read back with its history and reset to its default")
    void everyVariableChangesAndResets() throws Exception {
        String admin = superAdmin();

        for (ApplicationVariable variable : ApplicationVariable.values()) {
            String url = SETTINGS + "/" + variable.getKey();
            int shipped = variables.intValue(variable);
            int chosen = shipped == variable.getMax() ? variable.getMin() : variable.getMax();

            JsonNode changed = expect(200, putJson(admin, url, CHANGE.formatted(chosen, "Tried at the limit")));
            assertThat(changed.get("value").asInt()).isEqualTo(chosen);
            assertThat(changed.get("defaultValue").asInt()).isEqualTo(shipped);
            assertThat(changed.get("changed").asBoolean()).isTrue();
            assertThat(changed.get("lastChangedBy").asString()).isEqualTo("Alok Kumar");

            JsonNode reset = expect(200, postJson(admin, url + "/reset", RESET.formatted("Back to what shipped")));
            assertThat(reset.get("value").asInt()).isEqualTo(shipped);
            assertThat(reset.get("changed").asBoolean()).isFalse();

            JsonNode history = getJson(admin, url + "/history");
            assertThat(history).hasSize(2);
            assertThat(history.get(0).get("oldValue").asString()).isEqualTo(Integer.toString(chosen));
            assertThat(history.get(0).get("newValue").asString()).isEqualTo(Integer.toString(shipped));
            assertThat(history.get(0).get("note").asString()).isEqualTo("Back to what shipped");
            assertThat(history.get(1).get("oldValue").asString()).isEqualTo(Integer.toString(shipped));
            assertThat(history.get(1).get("note").asString()).isEqualTo("Tried at the limit");
            assertThat(history.get(1).get("changedBy").asString()).isEqualTo("Alok Kumar");
        }
    }

    @Test
    @DisplayName("a changed limit is what Find executives runs on, and a reset puts the configured one back")
    void aChangeReachesTheFeatureThatReadsIt() throws Exception {
        String admin = superAdmin();
        assertThat(getJson(admin, "/api/v1/executive-sourcing/config").get("maxCompaniesPerRun").asInt())
                .isEqualTo(5);

        expect(200, putJson(admin, COMPANIES, CHANGE.formatted(8, "Larger groups")));
        reload.recheck();
        assertThat(getJson(admin, "/api/v1/executive-sourcing/config").get("maxCompaniesPerRun").asInt())
                .isEqualTo(8);

        expect(200, postJson(admin, COMPANIES + "/reset", RESET.formatted("Eight ran past the deadline")));
        reload.recheck();
        assertThat(getJson(admin, "/api/v1/executive-sourcing/config").get("maxCompaniesPerRun").asInt())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("changing a variable to its default removes the override, as a reset would")
    void aChangeToTheDefaultIsAReset() throws Exception {
        String admin = superAdmin();
        expect(200, putJson(admin, COMPANIES, CHANGE.formatted(8, "Larger groups")));

        JsonNode back = expect(200, putJson(admin, COMPANIES, CHANGE.formatted(5, "Back by hand")));

        assertThat(back.get("changed").asBoolean()).isFalse();
        assertThat(overrides()).isZero();
        refused(409, "PLATFORM_SETTING_NOT_CHANGED",
                postJson(admin, COMPANIES + "/reset", RESET.formatted("Nothing to put back")));
    }

    @Test
    @DisplayName("a stored value that cannot serve is cleared by a change to the default, audited with what it held")
    void anUnusableStoredValueIsClearedByNamingTheDefault() throws Exception {
        String admin = superAdmin();
        storeUnusable("900");

        JsonNode cleared = expect(200, putJson(admin, COMPANIES, CHANGE.formatted(5, "Left over from wider bounds")));

        assertThat(cleared.get("value").asInt()).isEqualTo(5);
        assertThat(cleared.get("changed").asBoolean()).isFalse();
        assertThat(overrides()).isZero();
        assertThat(recordedChanges()).as("what served never changed").isZero();
        assertThat(audited("PLATFORM_SETTING_CHANGED", "\"from\": 5", "\"to\": 5", "\"discarded\": \"900\""))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a stored value that cannot serve is replaced by a change and removed by a reset, both audited")
    void anUnusableStoredValueIsReplacedOrReset() throws Exception {
        String admin = superAdmin();
        storeUnusable("many");

        expect(200, postJson(admin, COMPANIES + "/reset", RESET.formatted("Not a number")));

        assertThat(overrides()).isZero();
        assertThat(recordedChanges()).isZero();
        assertThat(audited("PLATFORM_SETTING_RESET", "\"from\": 5", "\"to\": 5", "\"discarded\": \"many\""))
                .isEqualTo(1);

        storeUnusable("900");
        JsonNode replaced = expect(200, putJson(admin, COMPANIES, CHANGE.formatted(8, "Larger groups")));

        assertThat(replaced.get("value").asInt()).isEqualTo(8);
        assertThat(recordedChanges()).isEqualTo(1);
        assertThat(audited("PLATFORM_SETTING_CHANGED", "\"from\": 5", "\"to\": 8", "\"discarded\": \"900\""))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a value that is no whole number in the variable's range, or no change, is refused on its field")
    void aBadValueIsRefusedOnItsField() throws Exception {
        String admin = superAdmin();

        for (String value : List.of("0", "26", "-3", "2.5", "eight", "99999999999")) {
            JsonNode problem = refused(400, "VALIDATION_FAILED", putJson(admin, COMPANIES,
                    CHANGE.formatted(value, "Tested")));
            assertThat(problem.get("fieldErrors").get("value").asString())
                    .isEqualTo("Enter a whole number from 1 to 25.");
        }
        assertThat(refused(400, "VALIDATION_FAILED", putJson(admin, COMPANIES, CHANGE.formatted(5, "Tested")))
                .get("fieldErrors").get("value").asString()).isEqualTo("That is its value now.");
        assertThat(refused(400, "VALIDATION_FAILED", putJson(admin, COMPANIES, CHANGE.formatted(8, " ")))
                .get("fieldErrors").has("note")).isTrue();
        assertThat(refused(400, "VALIDATION_FAILED", postJson(admin, COMPANIES + "/reset", RESET.formatted("")))
                .get("fieldErrors").has("note")).isTrue();
        assertThat(overrides()).isZero();
    }

    @Test
    @DisplayName("a key that is not on the list cannot be read, changed or reset, whatever it names")
    void aKeyOffTheListIsNotFound() throws Exception {
        String admin = superAdmin();

        for (String key : List.of("auth.jwt.access-token-ttl", "platform.require-two-factor",
                "enrichment.sourcing.neighbour-country-codes", "nothing")) {
            refused(404, "NOT_FOUND", putJson(admin, SETTINGS + "/" + key, CHANGE.formatted(1, "Tested")));
            refused(404, "NOT_FOUND", postJson(admin, SETTINGS + "/" + key + "/reset", RESET.formatted("Tested")));
            refused(404, "NOT_FOUND",
                    mvc.perform(get(SETTINGS + "/" + key + "/history").header("Authorization", bearer(admin)))
                            .andReturn());
        }
        assertThat(overrides()).isZero();
    }

    @Test
    @DisplayName("every change and reset is audited with the key and both values")
    void changesAreAudited() throws Exception {
        String admin = superAdmin();

        expect(200, putJson(admin, COMPANIES, CHANGE.formatted(8, "Larger groups")));
        expect(200, postJson(admin, COMPANIES + "/reset", RESET.formatted("Back to what shipped")));

        assertThat(audited("PLATFORM_SETTING_CHANGED", "\"from\": 5", "\"to\": 8")).isEqualTo(1);
        assertThat(audited("PLATFORM_SETTING_RESET", "\"from\": 8", "\"to\": 5")).isEqualTo(1);
    }

    @Test
    @DisplayName("nobody without the platform role is told any of it exists, nor a super admin without the action")
    void theRoutesAreGuarded() throws Exception {
        Firm firm = firm("Ordinary Firm", "admin");
        for (MockHttpServletRequestBuilder route : routes()) {
            refused(404, "NOT_FOUND",
                    mvc.perform(route.header("Authorization", bearer(firm.token()))).andReturn());
        }

        String admin = superAdmin();
        db.update("""
                DELETE FROM app_lm_role_action WHERE action_id =
                    (SELECT id FROM app_lm_action WHERE scope = 'PLATFORM' AND name = 'PLATFORM_SETTINGS_MANAGE')
                """);
        try {
            for (MockHttpServletRequestBuilder route : routes()) {
                refused(404, "NOT_FOUND", mvc.perform(route.header("Authorization", bearer(admin))).andReturn());
            }
        } finally {
            db.update("""
                    INSERT INTO app_lm_role_action (role_id, action_id)
                    SELECT r.id, a.id FROM app_lm_role r, app_lm_action a
                    WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
                      AND a.scope = 'PLATFORM' AND a.name = 'PLATFORM_SETTINGS_MANAGE'
                    """);
        }
        assertThat(overrides()).isZero();
    }

    @Test
    @DisplayName("the application can neither rewrite nor delete a recorded change")
    void theHistoryIsAppendOnly() throws Exception {
        expect(200, putJson(superAdmin(), COMPANIES, CHANGE.formatted(8, "Larger groups")));

        assertThatThrownBy(() -> app.update("UPDATE app_lm_platform_setting_change SET note = 'rewritten'"))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> app.update("DELETE FROM app_lm_platform_setting_change"))
                .hasStackTraceContaining("append-only");
    }

    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(get(SETTINGS), get(COMPANIES + "/history"),
                put(COMPANIES).contentType(MediaType.APPLICATION_JSON).content(CHANGE.formatted(8, "No")),
                post(COMPANIES + "/reset").contentType(MediaType.APPLICATION_JSON).content(RESET.formatted("No")));
    }

    private JsonNode refused(int status, String code, MvcResult result) throws Exception {
        JsonNode problem = expect(status, result);
        assertThat(problem.get("code").asString()).isEqualTo(code);
        return problem;
    }

    private void storeUnusable(String value) {
        db.update("INSERT INTO app_lm_platform_setting (key, value) VALUES (?, ?)",
                "enrichment.sourcing.max-companies-per-run", value);
    }

    private int recordedChanges() {
        return db.queryForObject("SELECT count(*) FROM app_lm_platform_setting_change", Integer.class);
    }

    private int overrides() {
        return db.queryForObject("SELECT count(*) FROM app_lm_platform_setting", Integer.class);
    }

    private int audited(String eventType, String... details) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = ? AND target_id = 'enrichment.sourcing.max-companies-per-run'
                  AND actor_user_id = (SELECT id FROM app_lm_user WHERE email = ?)
                """);
        for (String detail : details) {
            sql.append(" AND metadata::text LIKE '%").append(detail).append("%'");
        }
        return db.queryForObject(sql.toString(), Integer.class, eventType, "owner@" + domain);
    }
}
