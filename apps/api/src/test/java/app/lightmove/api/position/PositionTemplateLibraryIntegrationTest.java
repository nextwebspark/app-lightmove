package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The library as a super admin edits it, and what each kind of firm sees of the edit. */
@IntegrationTest
class PositionTemplateLibraryIntegrationTest extends PositionTemplateFlowSupport {

    @Test
    @DisplayName("a library edit reaches the next mandate of every firm that kept the library's version")
    void libraryEditReachesFirmsThatKeptIt() throws Exception {
        String admin = superAdmin();
        String keyword = uniqueKeyword();
        JsonNode created = expect(201, postJson(admin, LIBRARY, templateRequest(titleOf(keyword), keyword, "Quills", null)));
        String code = created.get("code").asText();

        JsonNode revised = expect(200, putJson(admin, LIBRARY + "/" + code,
                templateRequest(titleOf(keyword), keyword, "Group Quills", created.get("version").asLong())));
        assertThat(revised.get("version").asLong()).isGreaterThan(created.get("version").asLong());

        Firm follower = firm("Follower Firm", "sara");
        assertThat(draftedBrief(follower.token(), "Head of " + keyword).at("/details/department").asText())
                .isEqualTo("Group Quills");
    }

    @Test
    @DisplayName("a firm's copy survives a library edit, and the firm is told the library moved on")
    void customisedCopySurvivesALibraryEdit() throws Exception {
        String admin = superAdmin();
        String keyword = uniqueKeyword();
        JsonNode created = expect(201, postJson(admin, LIBRARY, templateRequest(titleOf(keyword), keyword, "Quills", null)));
        String code = created.get("code").asText();

        Firm firm = firm("Customising Firm", "sara");
        JsonNode asSeen = getJson(firm.token(), FIRM_TEMPLATES + "/" + code);
        assertThat(asSeen.get("origin").asText()).isEqualTo("LIBRARY");
        JsonNode copy = expect(200, putJson(firm.token(), FIRM_TEMPLATES + "/" + code, edited(asSeen, "Our Quills", true)));
        assertThat(copy.get("origin").asText()).isEqualTo("CUSTOMISED");
        assertThat(copy.get("libraryChangedSinceCustomised").asBoolean()).isFalse();

        expect(200, putJson(admin, LIBRARY + "/" + code,
                templateRequest(titleOf(keyword), keyword, "Group Quills", created.get("version").asLong())));

        assertThat(getJson(firm.token(), FIRM_TEMPLATES + "/" + code).get("libraryChangedSinceCustomised").asBoolean())
                .isTrue();
        assertThat(draftedBrief(firm.token(), "Head of " + keyword).at("/details/department").asText())
                .isEqualTo("Our Quills");
        assertThat(getJson(admin, LIBRARY + "/" + code).get("customisedByWorkspaces").asLong()).isEqualTo(1);

        JsonNode listed = find(getJson(admin, LIBRARY), code);
        assertThat(listed.get("customisedByWorkspaces").asLong()).isEqualTo(1);
        assertThat(listed.get("revisedByName").isNull()).isFalse();
        assertThat(listed.get("keywords").toString()).contains(keyword);
    }

    @Test
    @DisplayName("a save made against a version somebody has since replaced is refused, and theirs stands")
    void staleSaveIsRefused() throws Exception {
        String admin = superAdmin();
        String keyword = uniqueKeyword();
        JsonNode created = expect(201, postJson(admin, LIBRARY, templateRequest(titleOf(keyword), keyword, "Quills", null)));
        String url = LIBRARY + "/" + created.get("code").asText();
        long opened = created.get("version").asLong();

        expect(200, putJson(admin, url, templateRequest(titleOf(keyword), keyword, "First Save", opened)));
        expectRefused(409, "TEMPLATE_STALE",
                putJson(admin, url, templateRequest(titleOf(keyword), keyword, "Second Save", opened)));

        assertThat(getJson(admin, url).at("/body/department").asText()).isEqualTo("First Save");
    }

    @Test
    @DisplayName("archiving takes a template out of every picker, and the fallback cannot be archived")
    void archivingAndTheFallback() throws Exception {
        String admin = superAdmin();
        String keyword = uniqueKeyword();
        String code = expect(201, postJson(admin, LIBRARY, templateRequest(titleOf(keyword), keyword, "Quills", null)))
                .get("code").asText();
        Firm firm = firm("Picker Firm", "sara");
        assertThat(codesIn(getJson(firm.token(), PICKER))).contains(code);

        expect(200, patchJson(admin, LIBRARY + "/" + code + "/active", "{\"active\":false}"));
        assertThat(codesIn(getJson(firm.token(), PICKER))).doesNotContain(code);

        expectRefused(409, "TEMPLATE_FALLBACK_REQUIRED",
                patchJson(admin, LIBRARY + "/generic-executive/active", "{\"active\":false}"));
    }

    @Test
    @DisplayName("a template the brief could not hold is refused, naming every field at fault")
    void invalidTemplateIsRefused() throws Exception {
        String admin = superAdmin();

        JsonNode refused = expect(400, postJson(admin, LIBRARY, """
                {"title":"Unbalanced Officer","discipline":"FINANCE","seniority":"C_SUITE",
                 "body":{"currency":"dollars",
                   "competencies":[{"panel":"TECHNICAL","name":"Half a panel","weight":60}]}}
                """));

        assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(refused.at("/fieldErrors/body.currency").asText()).isNotBlank();
        assertThat(refused.at("/fieldErrors/body.competencies.technical").asText()).contains("total 100");
    }
}
