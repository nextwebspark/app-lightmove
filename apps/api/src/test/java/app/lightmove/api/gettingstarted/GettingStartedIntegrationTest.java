package app.lightmove.api.gettingstarted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** My positions' Getting started card: steps tick from the workspace's own rows, never from a click on the card. */
@IntegrationTest
class GettingStartedIntegrationTest extends FlowTestSupport {

    private static final String URL = "/api/v1/workspace/getting-started";

    private int clients;

    @Test
    @DisplayName("a new workspace starts with nothing done, and the newest position is where the steps lead")
    void freshWorkspace() throws Exception {
        String admin = adminOf("Fresh Firm");

        JsonNode view = view(admin);

        assertThat(view.get("dismissed").asBoolean()).isFalse();
        assertThat(view.get("focusProjectId").isNull()).isTrue();
        assertThat(stepNames(view)).contains("OPEN_POSITION", "WRITE_BRIEF", "FIND_COMPANIES", "MAP_EXECUTIVES",
                "INVITE_COLLEAGUE");
        assertThat(doneSteps(view)).isEmpty();
    }

    @Test
    @DisplayName("each step ticks from real work, and a drafted brief nobody touched does not count as written")
    void stepsTickFromData() throws Exception {
        String admin = adminOf("Working Firm");
        String projectId = createProject(admin);

        JsonNode afterCreate = view(admin);
        assertThat(doneSteps(afterCreate)).containsExactly("OPEN_POSITION");
        assertThat(afterCreate.get("focusProjectId").asText()).isEqualTo(projectId);

        mvc.perform(put("/api/v1/projects/" + projectId + "/position/details")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleTitle":"CFO","department":"Group Finance","location":null,
                                 "employmentType":null,"seniority":null,"responsibilities":[],
                                 "narrative":null,"fieldSources":{}}"""))
                .andExpect(status().isOk());
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName":"Target Company %d"}""".formatted(i)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","employerName":"Target Company 1"}"""))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"colleague@%s","role":"MEMBER"}]""".formatted(domain)))
                .andExpect(status().isOk());

        JsonNode view = view(admin);
        assertThat(doneSteps(view)).contains("OPEN_POSITION", "WRITE_BRIEF", "FIND_COMPANIES", "MAP_EXECUTIVES",
                "INVITE_COLLEAGUE");
        for (JsonNode step : view.get("steps")) {
            if (step.get("done").asBoolean()) {
                assertThat(step.get("completedAt").isNull()).as(step.toString()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("a step's first-seen time never moves")
    void stampIsKept() throws Exception {
        String admin = adminOf("Stamp Firm");
        createProject(admin);
        String first = stepOf(view(admin), "OPEN_POSITION").get("completedAt").asText();

        createProject(admin);

        assertThat(stepOf(view(admin), "OPEN_POSITION").get("completedAt").asText()).isEqualTo(first);
    }

    @Test
    @DisplayName("dismissing and skipping are kept for the person, and both can be undone")
    void dismissAndSkip() throws Exception {
        String admin = adminOf("Dismiss Firm");

        JsonNode skipped = body(mvc.perform(put(URL + "/steps/MAP_EXECUTIVES/skipped")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skipped\":true}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(stepOf(skipped, "MAP_EXECUTIVES").get("skipped").asBoolean()).isTrue();

        mvc.perform(put(URL + "/dismissed")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dismissed\":true}"))
                .andExpect(status().isOk());
        JsonNode reread = view(login("alok@" + domain));
        assertThat(reread.get("dismissed").asBoolean()).isTrue();
        assertThat(stepOf(reread, "MAP_EXECUTIVES").get("skipped").asBoolean()).isTrue();

        mvc.perform(put(URL + "/dismissed")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dismissed\":false}"))
                .andExpect(status().isOk());
        assertThat(view(admin).get("dismissed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a member is offered no invite step, and their dismissal is theirs alone")
    void memberView() throws Exception {
        String admin = adminOf("Member Firm");
        inviteAndAccept(admin, "Mona Member", "mona@" + domain, "MEMBER");
        String member = login("mona@" + domain);

        assertThat(stepNames(view(member))).doesNotContain("INVITE_COLLEAGUE");

        mvc.perform(put(URL + "/dismissed")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dismissed\":true}"))
                .andExpect(status().isOk());
        assertThat(view(admin).get("dismissed").asBoolean()).isFalse();
        // The admin's colleague joined, so their invite step is done.
        assertThat(doneSteps(view(admin))).contains("INVITE_COLLEAGUE");
    }

    @Test
    @DisplayName("a client representative has no checklist")
    void clientRefused() throws Exception {
        String admin = adminOf("Client Firm");
        String client = clientRepresentative(admin, "Rana Client", "rana@client-" + domain);

        mvc.perform(get(URL).header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unknown step is refused rather than stored")
    void unknownStep() throws Exception {
        String admin = adminOf("Typo Firm");

        mvc.perform(put(URL + "/steps/NOT_A_STEP/skipped")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skipped\":true}"))
                .andExpect(status().is4xxClientError());
    }

    private String adminOf(String workspaceName) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), workspaceName);
        return login(address);
    }

    private String createProject(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Gulf Holdings %d\"}".formatted(++clients)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode view(String token) throws Exception {
        return body(mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode stepOf(JsonNode view, String name) {
        for (JsonNode step : view.get("steps")) {
            if (step.get("step").asText().equals(name)) return step;
        }
        throw new AssertionError(name + " not offered in " + view);
    }

    private static List<String> stepNames(JsonNode view) {
        List<String> names = new ArrayList<>();
        view.get("steps").forEach(step -> names.add(step.get("step").asText()));
        return names;
    }

    private static List<String> doneSteps(JsonNode view) {
        List<String> names = new ArrayList<>();
        view.get("steps").forEach(step -> {
            if (step.get("done").asBoolean()) names.add(step.get("step").asText());
        });
        return names;
    }
}
