package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * The Contact section's own save, and the one field it may not touch.
 *
 * <p>What must hold: the save makes each channel hold exactly what was listed, a value's door is
 * kept unless the person respells it, a duplicate in one save is refused rather than silently
 * merged, someone who may only read the mandate may not write, and a profile URL the plugin captured
 * cannot be retyped through the profile's own PUT.
 */
@IntegrationTest
class CandidateContactsIntegrationTest extends FlowTestSupport {

    private String adminToken;

    @Test
    @DisplayName("the save replaces both channels with what was listed, keeping each value's door")
    void theSaveReplacesBothChannels() throws Exception {
        String projectId = mandate("Contacts Save Firm");
        String candidateId = executive(projectId, """
                {"fullName":"Sample Person","email":"typed@them.example","phone":"+971 50 000 0001"}""");

        JsonNode saved = contacts(projectId, candidateId, """
                {"emails":[{"value":"typed@them.example","kind":"work","verified":true},
                           {"value":"Second@Them.Example","kind":"personal","verified":false}],
                 "phones":[{"value":"+971 50 000 0002","kind":null,"verified":false}]}""",
                status().isOk());

        JsonNode emails = saved.get("contacts").get("emails");
        assertThat(emails).hasSize(2);
        assertThat(emails.get(0).get("address").asText()).isEqualTo("typed@them.example");
        assertThat(emails.get(0).get("kind").asText()).isEqualTo("work");
        assertThat(emails.get(0).get("verified").asBoolean()).isTrue();
        assertThat(emails.get(0).get("status").asText()).isEqualTo("Verified by researcher");
        assertThat(emails.get(0).get("source").asText()).isEqualTo("manual");
        assertThat(emails.get(1).get("address").asText()).isEqualTo("Second@Them.Example");
        JsonNode phones = saved.get("contacts").get("phones");
        assertThat(phones).hasSize(1);
        assertThat(phones.get(0).get("number").asText()).isEqualTo("+971 50 000 0002");
    }

    @Test
    @DisplayName("an address listed twice in one save is refused, whatever its case")
    void aDuplicateInOneSaveIsRefused() throws Exception {
        String projectId = mandate("Duplicate Contact Firm");
        String candidateId = executive(projectId, """
                {"fullName":"Sample Person"}""");

        assertThat(codeOf(mvc.perform(put(contactsUrl(projectId, candidateId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emails":[{"value":"one@them.example"},{"value":"ONE@them.example"}],
                                 "phones":[]}"""))
                .andExpect(status().isBadRequest())
                .andReturn()))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("the importer still recognises a person by any address they hold")
    void theImporterMatchesOnAnyAddress() throws Exception {
        String projectId = mandate("Any Address Firm");
        String candidateId = executive(projectId, """
                {"fullName":"Sample Person","email":"first@them.example"}""");
        contacts(projectId, candidateId, """
                {"emails":[{"value":"first@them.example"},{"value":"second@them.example"}],"phones":[]}""",
                status().isOk());

        JsonNode again = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sample Person","email":"third@them.example"}"""))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(again.get("code").asText()).isEqualTo("CANDIDATE_ALREADY_MAPPED");
    }

    @Test
    @DisplayName("a captured profile's LinkedIn URL cannot be retyped; a hand-added one can")
    void aCapturedProfileUrlIsLocked() throws Exception {
        String projectId = mandate("Locked Url Firm");
        String captured = executive(projectId, """
                {"fullName":"Captured Person","source":"extension",
                 "linkedinUrl":"https://www.linkedin.com/in/captured-person"}""");
        String typed = executive(projectId, """
                {"fullName":"Typed Person","linkedinUrl":"https://www.linkedin.com/in/typed-person"}""");

        assertThat(codeOf(mvc.perform(put(candidatesUrl(projectId) + "/" + captured)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Captured Person","source":"extension",
                                 "linkedinUrl":"https://www.linkedin.com/in/someone-else"}"""))
                .andExpect(status().isConflict())
                .andReturn()))
                .isEqualTo("CANDIDATE_PROFILE_URL_LOCKED");

        // Replaying the captured URL unchanged is how every other section saves; it must pass.
        mvc.perform(put(candidatesUrl(projectId) + "/" + captured)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Captured Person","title":"CFO","source":"extension",
                                 "linkedinUrl":"https://www.linkedin.com/in/captured-person"}"""))
                .andExpect(status().isOk());

        mvc.perform(put(candidatesUrl(projectId) + "/" + typed)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Typed Person","linkedinUrl":"https://www.linkedin.com/in/typed-person-2"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a client representative may read the mandate and may not edit its contacts")
    void aClientRepresentativeMayNotEdit() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Client Rep Contacts Firm");
        adminToken = login(alok);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Rep Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Group CFO"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
        String candidateId = executive(projectId, """
                {"fullName":"Sample Person"}""");
        String repEmail = "chair@rep-contacts.example";
        String representativeId = body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                """.formatted(repEmail)))
                .andReturn()).get("id").asText();
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andReturn()).get("accessToken").asText();
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}""".formatted(representativeId)))
                .andExpect(status().isOk());

        mvc.perform(put(contactsUrl(projectId, candidateId))
                        .header("Authorization", "Bearer " + rep)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emails":[{"value":"rep@them.example"}],"phones":[]}"""))
                .andExpect(status().isForbidden());
    }

    private JsonNode contacts(String projectId, String candidateId, String content,
                              org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return body(mvc.perform(put(contactsUrl(projectId, candidateId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(expected)
                .andReturn());
    }

    private static String contactsUrl(String projectId, String candidateId) {
        return candidatesUrl(projectId) + "/" + candidateId + "/contacts";
    }

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }

    private String executive(String projectId, String content) throws Exception {
        return body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Contacts Client"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Group CFO"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
