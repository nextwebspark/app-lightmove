package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The client registry's own CRUD, direct: create/list/get/update and tenant isolation. The create-time
 * duplicate-name 409 already lives in {@link ProjectFlowIntegrationTest#clientNamesAreUniquePerWorkspace}
 * and representative invites in {@link ClientAccessIntegrationTest} — this file does not repeat either.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ClientFlowIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a custom client is created with an empty mandate count and no contacts")
    void createHappyPath() throws Exception {
        String admin = adminOf("New Client Firm");

        mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Meridian Energy","sector":"Energy","hqCountry":"UAE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Meridian Energy"))
                .andExpect(jsonPath("$.sector").value("Energy"))
                .andExpect(jsonPath("$.hqCountry").value("UAE"))
                .andExpect(jsonPath("$.activeMandates").value(0))
                .andExpect(jsonPath("$.deliveredMandates").value(0))
                .andExpect(jsonPath("$.contacts.length()").value(0));
    }

    @Test
    @DisplayName("a blank custom name is refused before it ever reaches the uniqueness check")
    void blankCustomNameIsRejected() throws Exception {
        String admin = adminOf("Blank Name Firm");

        MvcResult rejected = mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":""}"""))
                .andReturn();
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(rejected)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("the list carries every client in the workspace, alphabetically by name")
    void listReturnsEveryClientInNameOrder() throws Exception {
        String admin = adminOf("Listing Firm");
        createClient(admin, "Zenith Retail");
        createClient(admin, "Agthia Group");

        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Agthia Group"))
                .andExpect(jsonPath("$[1].name").value("Zenith Retail"));
    }

    @Test
    @DisplayName("the detail view carries the editable fields plus empty representatives and mandates")
    void getReturnsFullDetail() throws Exception {
        String admin = adminOf("Detail Firm");
        String clientId = createClient(admin, "Meridian Energy");

        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.name").value("Meridian Energy"))
                .andExpect(jsonPath("$.activeMandates").value(0))
                .andExpect(jsonPath("$.deliveredMandates").value(0))
                .andExpect(jsonPath("$.representatives.length()").value(0))
                .andExpect(jsonPath("$.mandates.length()").value(0));
    }

    @Test
    @DisplayName("an update changes the editable fields, and renaming to the same name in a new case is a no-op")
    void updateChangesFieldsAndToleratesItsOwnCaseChange() throws Exception {
        String admin = adminOf("Update Firm");
        String clientId = createClient(admin, "Meridian Energy");

        mvc.perform(patch("/api/v1/clients/" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MERIDIAN ENERGY","sector":"Energy","hqCountry":"Saudi Arabia"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MERIDIAN ENERGY"))
                .andExpect(jsonPath("$.sector").value("Energy"))
                .andExpect(jsonPath("$.hqCountry").value("Saudi Arabia"));
    }

    @Test
    @DisplayName("renaming a client onto another client's name is a 409, whatever its case")
    void updateDuplicateNameIsRejected() throws Exception {
        String admin = adminOf("Update Duplicate Firm");
        createClient(admin, "Meridian Energy");
        String otherId = createClient(admin, "Agthia Group");

        MvcResult duplicate = mvc.perform(patch("/api/v1/clients/" + otherId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"meridian energy"}"""))
                .andReturn();
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo("CLIENT_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("nothing of one workspace's clients is visible or reachable from another")
    void tenantIsolation() throws Exception {
        String admin = adminOf("Isolation Firm");
        String clientId = createClient(admin, "Meridian Energy");

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Rival Isolation Firm");
        String rival = login(rivalEmail);

        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + rival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + rival))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/v1/clients/" + clientId)
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked"}"""))
                .andExpect(status().isNotFound());
    }

    private String adminOf(String workspaceName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), workspaceName);
        return login(alok);
    }

    private String createClient(String token, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
