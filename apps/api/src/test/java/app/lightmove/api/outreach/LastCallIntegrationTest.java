package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** Last call: the newest call anyone on the team logged with a person, on any position, in the conversation read. */
@IntegrationTest
class LastCallIntegrationTest extends FlowTestSupport {

    private String yara;
    private String sara;
    private String cfoSearch;
    private String creditRiskSearch;
    private String laylaOnCfo;
    private String laylaOnCreditRisk;

    @BeforeEach
    void setUp() throws Exception {
        String yaraEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", yaraEmail), "Calling Partners");
        yara = login(yaraEmail);
        String saraEmail = "sara@" + domain;
        inviteAndAccept(yara, "Sara Al-Mansour", saraEmail, "MEMBER");
        sara = login(saraEmail);
        String clientId = ownClient(yara);
        cfoSearch = mandate(yara, clientId, "Group CFO");
        creditRiskSearch = mandate(sara, clientId, "Head of Credit Risk");
        laylaOnCfo = executive(yara, cfoSearch, "layla@" + domain);
        laylaOnCreditRisk = executive(sara, creditRiskSearch, "layla@" + domain);
    }

    @Test
    @DisplayName("the newest call on any position, with how it went and who logged it, and never the words")
    void theNewestCallOnAnyPosition() throws Exception {
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        logged(yara, cfoSearch, laylaOnCfo, "call", "connected", "Spoke about the move", Duration.ofDays(10));
        logged(sara, creditRiskSearch, laylaOnCreditRisk, "call", "no_answer", "", twoDaysAgo);
        logged(sara, creditRiskSearch, laylaOnCreditRisk, "whatsapp", "sent", "", Duration.ofHours(3));

        JsonNode lastCall = lastCallOnCfo();
        assertThat(Instant.parse(lastCall.get("at").asText())).isEqualTo(twoDaysAgo);
        assertThat(lastCall.get("outcome").asText()).isEqualTo("no_answer");
        assertThat(lastCall.get("byName").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(lastCall.has("projectId")).isFalse();
    }

    @Test
    @DisplayName("a deleted call is no longer the last call")
    void aDeletedCallIsPassedOver() throws Exception {
        logged(yara, cfoSearch, laylaOnCfo, "call", "connected", "Spoke about the move", Duration.ofDays(10));
        String newest = logged(sara, creditRiskSearch, laylaOnCreditRisk, "call", "voicemail", "", Duration.ofDays(1));
        as(sara, delete(notesOf(creditRiskSearch, laylaOnCreditRisk) + "/" + newest))
                .andExpect(status().is2xxSuccessful());

        JsonNode lastCall = lastCallOnCfo();
        assertThat(lastCall.get("outcome").asText()).isEqualTo("connected");
        assertThat(lastCall.get("byName").asText()).isEqualTo("Yara Haddad");
    }

    @Test
    @DisplayName("no call logged reads none, and the executive's own record, which a client seat reads, never carries it")
    void noCallReadsNone() throws Exception {
        assertThat(lastCallOnCfo().isNull()).isTrue();

        logged(yara, cfoSearch, laylaOnCfo, "call", "connected", "Spoke about the move", Duration.ofDays(1));
        JsonNode executive = body(as(yara, get("/api/v1/projects/" + cfoSearch + "/candidates/" + laylaOnCfo))
                .andExpect(status().isOk()).andReturn());
        assertThat(executive.toString()).doesNotContain("lastCall");
    }

    private JsonNode lastCallOnCfo() throws Exception {
        return body(as(yara, get("/api/v1/projects/" + cfoSearch + "/outreach/conversations/" + laylaOnCfo))
                .andExpect(status().isOk()).andReturn()).at("/keyDates/lastCall");
    }

    private String logged(String token, String projectId, String candidateId, String kind, String outcome,
                          String words, Duration ago) throws Exception {
        return logged(token, projectId, candidateId, kind, outcome, words, Instant.now().minus(ago));
    }

    private String logged(String token, String projectId, String candidateId, String kind, String outcome,
                          String words, Instant at) throws Exception {
        return body(as(token, post(notesOf(projectId, candidateId)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind":"%s","outcome":"%s","body":"%s","occurredAt":"%s"}"""
                        .formatted(kind, outcome, words, at)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private static String notesOf(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/notes";
    }

    private String executive(String token, String projectId, String emailAddress) throws Exception {
        return body(as(token, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Layla Haddad","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/layla-haddad-%d","email":"%s"}
                        """.formatted(Math.abs(emailAddress.hashCode()), emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate(String token, String clientId, String title) throws Exception {
        return body(as(token, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"%s\"}".formatted(clientId, title)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String ownClient(String token) throws Exception {
        return body(as(token, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
