package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code lightmove.data-protection.enabled: false} takes epic #917's people controls off every route. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.data-protection.enabled=false")
class DataProtectionSwitchedOffIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("the config says so, and the list, erasure, export, restriction and retention routes answer an admin 404")
    void everyControlIsNotFound() throws Exception {
        String address = "switchedoff@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), "Switched Off Firm");
        String admin = login(address);
        UUID someone = UUID.randomUUID();
        String people = """
                {"personIds":["%s"]}""".formatted(someone);

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
                post("/api/v1/privacy/links/not-a-real-token/objection"));
        for (MockHttpServletRequestBuilder route : routes) {
            assertThat(codeOf(mvc.perform(route.header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()).andReturn())).isEqualTo("NOT_FOUND");
        }
    }
}
