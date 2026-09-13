package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A firm's own templates: a copy of a library template shadows it for that firm alone, a reset hands
 * the library's back, a hidden template leaves the picker and title matching, and only an admin may
 * change any of it.
 */
@IntegrationTest
class WorkspacePositionTemplateIntegrationTest extends PositionTemplateFlowSupport {

    private static final String CFO = FIRM_TEMPLATES + "/chief-financial-officer";

    @Test
    @DisplayName("customising a library template gives the firm its own copy, and nobody else")
    void customisingIsTheFirmsAlone() throws Exception {
        Firm firm = firm("Own Copy Firm", "alok");
        JsonNode library = getJson(firm.token(), CFO);
        assertThat(library.get("origin").asText()).isEqualTo("LIBRARY");

        JsonNode copy = expect(200, putJson(firm.token(), CFO, edited(library, "Group Finance & Treasury", true)));
        assertThat(copy.get("origin").asText()).isEqualTo("CUSTOMISED");

        JsonNode firmList = getJson(firm.token(), FIRM_TEMPLATES);
        JsonNode listedCopy = find(firmList, "chief-financial-officer");
        assertThat(listedCopy.get("revisedByName").isNull()).isFalse();
        assertThat(listedCopy.get("customisedByWorkspaces").isNull()).isTrue();
        assertThat(find(firmList, "chief-risk-officer").get("revisedByName").isNull()).isTrue();

        JsonNode picker = getJson(firm.token(), PICKER);
        assertThat(codesIn(picker)).containsOnlyOnce("chief-financial-officer");
        assertThat(find(picker, "chief-financial-officer").get("shared").asBoolean()).isFalse();
        assertThat(draftedBrief(firm.token(), "Chief Financial Officer").at("/details/department").asText())
                .isEqualTo("Group Finance & Treasury");

        Firm neighbour = firm("Library Firm", "sara");
        assertThat(find(getJson(neighbour.token(), PICKER), "chief-financial-officer").get("shared").asBoolean())
                .isTrue();
        assertThat(getJson(neighbour.token(), CFO).get("origin").asText()).isEqualTo("LIBRARY");
        assertThat(draftedBrief(neighbour.token(), "Chief Financial Officer").at("/details/department").asText())
                .isEqualTo("Finance");
    }

    @Test
    @DisplayName("a copy taken from a library version the editor did not see is refused")
    void staleCustomisationIsRefused() throws Exception {
        Firm firm = firm("Stale Copy Firm", "alok");
        JsonNode library = getJson(firm.token(), CFO);
        String payload = edited(library, "Finance", true)
                .replace("\"version\":" + library.get("version").asLong(),
                        "\"version\":" + (library.get("version").asLong() + 1));

        expectRefused(409, "TEMPLATE_STALE", putJson(firm.token(), CFO, payload));
    }

    @Test
    @DisplayName("resetting a copy hands the firm the library's template back")
    void resettingRestoresTheLibrary() throws Exception {
        Firm firm = firm("Reset Firm", "alok");
        expect(200, putJson(firm.token(), CFO, edited(getJson(firm.token(), CFO), "Our Finance", true)));

        mvc.perform(delete(CFO).header("Authorization", bearer(firm.token())))
                .andExpect(status().isNoContent());

        assertThat(getJson(firm.token(), CFO).get("origin").asText()).isEqualTo("LIBRARY");
        assertThat(find(getJson(firm.token(), PICKER), "chief-financial-officer").get("shared").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a hidden library template leaves the picker and title matching, and returns when shown")
    void hidingAndShowing() throws Exception {
        Firm firm = firm("Hiding Firm", "alok");
        String riskOfficer = FIRM_TEMPLATES + "/chief-risk-officer";

        expect(200, patchJson(firm.token(), riskOfficer + "/hidden", "{\"hidden\":true}"));
        assertThat(codesIn(getJson(firm.token(), PICKER))).doesNotContain("chief-risk-officer");
        assertThat(find(getJson(firm.token(), FIRM_TEMPLATES), "chief-risk-officer").get("origin").asText())
                .isEqualTo("HIDDEN");
        // The title falls through to the generic brief, which is N-1 where the risk template is C-suite.
        assertThat(draftedBrief(firm.token(), "Chief Risk Officer").at("/details/seniority").asText())
                .isEqualTo("N_MINUS_1");

        expect(200, patchJson(firm.token(), riskOfficer + "/hidden", "{\"hidden\":false}"));
        assertThat(codesIn(getJson(firm.token(), PICKER))).contains("chief-risk-officer");

        expectRefused(409, "TEMPLATE_FALLBACK_REQUIRED",
                patchJson(firm.token(), FIRM_TEMPLATES + "/generic-executive/hidden", "{\"hidden\":true}"));
    }

    @Test
    @DisplayName("a template the firm wrote drafts its mandates, and is gone once deleted")
    void ownTemplate() throws Exception {
        Firm firm = firm("Author Firm", "alok");
        String keyword = uniqueKeyword();

        JsonNode created = expect(201, postJson(firm.token(), FIRM_TEMPLATES,
                templateRequest(titleOf(keyword), keyword, "Quills", null)));
        assertThat(created.get("origin").asText()).isEqualTo("OWN");
        String code = created.get("code").asText();
        assertThat(draftedBrief(firm.token(), "Head of " + keyword).at("/details/department").asText())
                .isEqualTo("Quills");

        mvc.perform(delete(FIRM_TEMPLATES + "/" + code).header("Authorization", bearer(firm.token())))
                .andExpect(status().isNoContent());
        assertThat(codesIn(getJson(firm.token(), PICKER))).doesNotContain(code);
    }

    @Test
    @DisplayName("managing the firm's templates is an admin's action; a member still has the picker")
    void membersCannotManageTemplates() throws Exception {
        Firm firm = firm("Gated Firm", "alok");
        String memberAddress = "sara@" + domain;
        inviteAndAccept(firm.token(), "Sara Staff", memberAddress, "MEMBER");
        String member = login(memberAddress);

        mvc.perform(get(FIRM_TEMPLATES).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get(PICKER).header("Authorization", bearer(member)))
                .andExpect(status().isOk());
    }
}
