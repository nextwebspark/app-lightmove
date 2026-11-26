package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.service.ConsentExpiryScanner;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code lightmove.data-protection.enabled: false} takes epic #917's people controls off every route. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.data-protection.enabled=false")
class DataProtectionSwitchedOffIntegrationTest extends FlowTestSupport {

    @Autowired ApplicationContext context;
    @Autowired @FixtureJdbc JdbcTemplate db;

    @Test
    @DisplayName("the config says so, and the list, erasure, export, restriction and retention routes answer an admin 404")
    void everyControlIsNotFound() throws Exception {
        String address = "switchedoff@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), "Switched Off Firm");
        String admin = login(address);
        UUID someone = UUID.randomUUID();
        String people = """
                {"personIds":["%s"]}""".formatted(someone);

        assertThat(context.getBeanNamesForType(ConsentExpiryScanner.class)).as("no consent scan runs").isEmpty();
        assertThat(body(mvc.perform(get("/api/v1/data-protection/config").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn()).get("enabled").asBoolean()).isFalse();

        List<MockHttpServletRequestBuilder> routes = List.of(
                get("/api/v1/workspace/suppressions"),
                post("/api/v1/workspace/suppressions").content("""
                        {"email":"noor@example.com"}"""),
                delete("/api/v1/workspace/suppressions/" + someone),
                get("/api/v1/candidates/" + someone + "/erase-preview"),
                post("/api/v1/candidates/" + someone + "/erase").content("""
                        {"confirmName":"Noor Haddad"}"""),
                get("/api/v1/candidates/" + someone + "/subject-access-export"),
                put("/api/v1/candidates/" + someone + "/processing-restriction").content("""
                        {"restricted":true,"reason":"Contesting their record"}"""),
                get("/api/v1/workspace/retention"),
                put("/api/v1/workspace/retention").content("""
                        {"months":12}"""),
                get("/api/v1/workspace/retention/due"),
                post("/api/v1/workspace/retention/due/keep").content(people),
                post("/api/v1/workspace/retention/due/erase").content(people),
                get("/api/v1/workspace/candidate-privacy"),
                put("/api/v1/workspace/candidate-privacy").content("""
                        {"defaultLawfulBasis":"consent"}"""),
                put("/api/v1/candidates/" + someone + "/lawful-basis").content("""
                        {"lawfulBasis":"consent"}"""),
                post("/api/v1/candidates/" + someone + "/privacy-notice"),
                post("/api/v1/candidates/" + someone + "/privacy-notice/marked").content("""
                        {"notifiedOn":"2026-01-01"}"""),
                post("/api/v1/candidates/bulk/privacy-notice").content(people),
                get("/api/v1/privacy/links/not-a-real-token"),
                post("/api/v1/privacy/links/not-a-real-token/objection"),
                post("/api/v1/privacy/links/not-a-real-token/opt-out"),
                post("/api/v1/privacy/links/not-a-real-token/consents").content("""
                        {"answers":[{"consentId":"%s","answer":"given"}]}""".formatted(someone)),
                post("/api/v1/candidates/" + someone + "/consent-requests").content("""
                        {"purposes":["keep_on_file"]}"""),
                post("/api/v1/candidates/bulk/consent-requests").content("""
                        {"personIds":["%s"],"purposes":["keep_on_file"]}""".formatted(someone)),
                post("/api/v1/candidates/" + someone + "/consents/recorded").content("""
                        {"purpose":"keep_on_file","answer":"given","answeredOn":"2026-01-01"}"""),
                get("/api/v1/projects/" + someone + "/share-consents"),
                post("/api/v1/projects/" + someone + "/candidates/" + someone + "/share-consent-request")
                        .content("{}"),
                get("/api/v1/candidates/export/consent-register"),
                get("/api/v1/projects/" + someone + "/export/consent-register"));
        for (MockHttpServletRequestBuilder route : routes) {
            assertThat(codeOf(mvc.perform(route.header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()).andReturn())).isEqualTo("NOT_FOUND");
        }
    }

    @Test
    @DisplayName("Do not contact stays the team's own flag: its reason kept, nobody suppressed, and it clears")
    void doNotContactIsTheTeamsFlag() throws Exception {
        String address = "flag@" + domain;
        String workspace = createWorkspace(verifiedUser("Alok Kumar", address), "Flag Firm");
        String admin = login(address);
        String clientId = body(mvc.perform(post("/api/v1/clients").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"customName\":\"Finance Unit\"}"))
                .andReturn()).get("id").asText();
        String position = body(mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String person = body(mvc.perform(post("/api/v1/projects/" + position + "/candidates")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"Noor Haddad","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/"}"""))
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();

        for (boolean doNotContact : List.of(true, false)) {
            mvc.perform(put("/api/v1/candidates/" + person + "/do-not-contact")
                            .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"doNotContact":%s,"reason":"Works at the client"}""".formatted(doNotContact)))
                    .andExpect(status().isOk());
            if (doNotContact) {
                assertThat(db.queryForObject("select do_not_contact_reason from app_lm_person where id = ?::uuid",
                        String.class, person)).isEqualTo("Works at the client");
            }
        }
        assertThat(db.queryForObject("select do_not_contact from app_lm_person where id = ?::uuid", Boolean.class,
                person)).isFalse();
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_suppression where workspace_id = ?::uuid",
                Long.class, workspace)).isZero();
    }
}
