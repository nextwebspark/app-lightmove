package app.lightmove.api.startpage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Where a member's Uncava opens: kept per person and workspace, checked against their access on save and on read. */
@IntegrationTest
class StartPageIntegrationTest extends FlowTestSupport {

    private static final String START_PAGE = "/api/v1/me/start-page";

    @Test
    @DisplayName("Home until chosen; a chosen page rides /me, is the member's own, and Home forgets it")
    void chosenPerMemberAndCarriedOnMe() throws Exception {
        String admin = firm("Start Page Firm");
        String colleagueEmail = "maya@" + domain;
        inviteAndAccept(admin, "Maya Saleh", colleagueEmail, "MEMBER");
        String colleague = login(colleagueEmail);

        assertThat(startPageOnMe(admin).get("kind").asText()).isEqualTo("HOME");

        JsonNode saved = body(save(admin, "{\"kind\":\"CANDIDATES\"}").andExpect(status().isOk()).andReturn());
        assertThat(saved.get("kind").asText()).isEqualTo("CANDIDATES");
        assertThat(saved.get("available").asBoolean()).isTrue();
        assertThat(startPageOnMe(admin).get("kind").asText()).isEqualTo("CANDIDATES");
        assertThat(startPageOnMe(colleague).get("kind").asText()).isEqualTo("HOME");

        String projectId = createProject(admin, "Chief Financial Officer");
        JsonNode position = body(save(admin, """
                {"kind":"POSITION","projectId":"%s","section":"OUTREACH"}""".formatted(projectId))
                .andExpect(status().isOk()).andReturn());
        assertThat(position.get("projectId").asText()).isEqualTo(projectId);
        assertThat(position.get("section").asText()).isEqualTo("OUTREACH");

        save(admin, "{\"kind\":\"HOME\"}").andExpect(status().isOk());
        assertThat(startPageOnMe(admin).get("kind").asText()).isEqualTo("HOME");
    }

    @Test
    @DisplayName("a page the member cannot open is refused with one answer, a malformed one is a validation error")
    void unreachablePagesAreRefused() throws Exception {
        String admin = firm("Start Page Rules Firm");
        String colleagueEmail = "rami@" + domain;
        inviteAndAccept(admin, "Rami Nasser", colleagueEmail, "MEMBER");
        String colleague = login(colleagueEmail);
        String adminsPosition = createProject(admin, "Head of Retail");
        String adminsPrivateDashboard = createDashboard(admin, "Leadership weekly", false);

        assertThat(refusal(save(colleague, """
                {"kind":"POSITION","projectId":"%s","section":"BRIEF"}""".formatted(adminsPosition))))
                .isEqualTo("START_PAGE_UNAVAILABLE");
        assertThat(refusal(save(colleague, """
                {"kind":"DASHBOARD","dashboardId":"%s"}""".formatted(adminsPrivateDashboard))))
                .isEqualTo("START_PAGE_UNAVAILABLE");
        assertThat(refusal(save(colleague, """
                {"kind":"DASHBOARD","dashboardId":"00000000-0000-0000-0000-000000000001"}""")))
                .isEqualTo("START_PAGE_UNAVAILABLE");

        String colleaguesPosition = createProject(colleague, "Operations Director");
        save(colleague, """
                {"kind":"POSITION","projectId":"%s","section":"UNIVERSE"}""".formatted(colleaguesPosition))
                .andExpect(status().isOk());

        assertThat(codeOf(save(colleague, "{\"kind\":\"DASHBOARD\"}")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(colleague, """
                {"kind":"POSITION","projectId":"%s","section":"PIPELINE"}""".formatted(colleaguesPosition))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(colleague, "{\"kind\":\"INBOX\"}")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a page lost since it was chosen reads as unavailable, and a deleted one goes with its row")
    void aLostPageReadsAsUnavailable() throws Exception {
        String admin = firm("Start Page Lost Firm");
        String colleagueEmail = "lina@" + domain;
        inviteAndAccept(admin, "Lina Farouk", colleagueEmail, "MEMBER");
        String colleague = login(colleagueEmail);
        String shared = createDashboard(admin, "Pipeline overview", true);

        save(colleague, "{\"kind\":\"DASHBOARD\",\"dashboardId\":\"%s\"}".formatted(shared)).andExpect(status().isOk());
        assertThat(startPageOnMe(colleague).get("available").asBoolean()).isTrue();

        JsonNode dashboard = body(mvc.perform(get("/api/v1/dashboards").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn()).get(0);
        mvc.perform(put("/api/v1/dashboards/" + shared).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pipeline overview\",\"shared\":false,\"widgets\":[],\"version\":%d}"
                                .formatted(dashboard.get("version").asLong())))
                .andExpect(status().isOk());
        JsonNode lost = startPageOnMe(colleague);
        assertThat(lost.get("kind").asText()).isEqualTo("DASHBOARD");
        assertThat(lost.get("available").asBoolean()).isFalse();
        assertThat(body(mvc.perform(get(START_PAGE).header("Authorization", "Bearer " + colleague))
                .andExpect(status().isOk()).andReturn()).get("available").asBoolean()).isFalse();

        mvc.perform(delete("/api/v1/dashboards/" + shared).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(startPageOnMe(colleague).get("kind").asText()).isEqualTo("HOME");
    }

    @Test
    @DisplayName("a pure client may open on Positions or a position, never on a staff page")
    void aPureClientIsOfferedTheirOwnPages() throws Exception {
        String admin = firm("Start Page Client Firm");
        String pureClient = clientRepresentative(admin, "Sami Haddad", "sami@client-" + domain);
        String position = createProject(admin, "Group CFO");

        assertThat(refusal(save(pureClient, "{\"kind\":\"CANDIDATES\"}"))).isEqualTo("START_PAGE_UNAVAILABLE");
        assertThat(refusal(save(pureClient, "{\"kind\":\"TASKS\"}"))).isEqualTo("START_PAGE_UNAVAILABLE");
        assertThat(refusal(save(pureClient, """
                {"kind":"POSITION","projectId":"%s","section":"BRIEF"}""".formatted(position))))
                .isEqualTo("START_PAGE_UNAVAILABLE");

        save(pureClient, "{\"kind\":\"POSITIONS\"}").andExpect(status().isOk());
        assertThat(startPageOnMe(pureClient).get("kind").asText()).isEqualTo("POSITIONS");
    }

    private String firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        return login(address);
    }

    private String createProject(String token, String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"%s Group\"}".formatted(positionTitle)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"%s\"}".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String createDashboard(String token, String name, boolean shared) throws Exception {
        return body(mvc.perform(post("/api/v1/dashboards").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"shared\":%s,\"widgets\":[]}".formatted(name, shared)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private JsonNode startPageOnMe(String token) throws Exception {
        return body(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).get("startPage");
    }

    private ResultActions save(String token, String json) throws Exception {
        return mvc.perform(put(START_PAGE).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String refusal(ResultActions response) throws Exception {
        return codeOf(response.andExpect(status().isUnprocessableContent()).andReturn());
    }
}
