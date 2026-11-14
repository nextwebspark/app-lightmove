package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Recently approached: before an email or a sequence, who on the team has already reached the person on any
 * position, and where else they are in process — a warning, never a skip, inside the workspace's window.
 */
@IntegrationTest
class RecentApproachIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc private JdbcTemplate jdbc;

    private String yara;
    private String sara;
    private String saraId;
    private String yaraId;
    private String workspaceId;
    private String cfoSearch;
    private String creditRiskSearch;
    private String laylaOnCfo;
    private String laylaOnCreditRisk;
    private String laylaPersonId;

    @BeforeEach
    void setUp() throws Exception {
        String yaraEmail = "yara@" + domain;
        workspaceId = createWorkspace(verifiedUser("Yara Haddad", yaraEmail), "Approach Partners");
        yara = login(yaraEmail);
        String saraEmail = "sara@" + domain;
        inviteAndAccept(yara, "Sara Al-Mansour", saraEmail, "MEMBER");
        sara = login(saraEmail);
        yaraId = userIdOf(yara);
        saraId = userIdOf(sara);
        String clientId = ownClient(yara);
        cfoSearch = mandate(yara, clientId, "Group CFO");
        creditRiskSearch = mandate(sara, clientId, "Head of Credit Risk");
        laylaOnCfo = executive(yara, cfoSearch, "Layla Haddad", "layla@" + domain);
        laylaOnCreditRisk = executive(sara, creditRiskSearch, "Layla Haddad", "layla@" + domain);
        laylaPersonId = jdbc.queryForObject("select person_id::text from app_lm_project_candidate where id = ?::uuid",
                String.class, laylaOnCfo);
    }

    @Test
    @DisplayName("a teammate's email on another position warns in the composer and the enrol list, never skipping")
    void aTeammatesEmailElsewhereWarns() throws Exception {
        email(saraId, creditRiskSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(12));

        JsonNode composer = composerApproach();
        assertThat(composer.get("tier").asText()).isEqualTo("CAUTION");
        assertThat(composer.get("windowDays").asInt()).isEqualTo(90);
        JsonNode touch = composer.at("/touches/0");
        assertThat(touch.get("kind").asText()).isEqualTo("EMAILED");
        assertThat(touch.get("byName").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(touch.get("positionTitle").asText()).isEqualTo("Head of Credit Risk");
        assertThat(touch.get("samePosition").asBoolean()).isFalse();
        assertThat(composer.at("/elsewhere/0/positionTitle").asText()).isEqualTo("Head of Credit Risk");
        assertThat(composer.at("/elsewhere/0/inProcess").asBoolean()).isTrue();
        assertThat(composer.at("/elsewhere/0/visible").asBoolean()).isTrue();
        assertThat(composer.toString()).doesNotContain("Confidential introduction");

        JsonNode row = enrolRow();
        assertThat(row.get("skipReason").isNull()).isTrue();
        assertThat(row.at("/recentApproach/tier").asText()).isEqualTo("CAUTION");
        assertThat(row.at("/recentApproach/touches/0/byName").asText()).isEqualTo("Sara Al-Mansour");
    }

    @Test
    @DisplayName("a position the caller cannot open is only another position, with its stage")
    void aPositionTheCallerCannotOpenStaysUnnamed() throws Exception {
        email(yaraId, cfoSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(4));

        JsonNode approach = body(as(sara, get("/api/v1/projects/" + creditRiskSearch + "/outreach/conversations/"
                        + laylaOnCreditRisk)).andExpect(status().isOk()).andReturn()).at("/composer/recentApproach");
        assertThat(approach.get("tier").asText()).isEqualTo("CAUTION");
        JsonNode touch = approach.at("/touches/0");
        assertThat(touch.get("byName").asText()).isEqualTo("Yara Haddad");
        assertThat(touch.get("positionHidden").asBoolean()).isTrue();
        assertThat(touch.get("projectId").isNull()).isTrue();
        assertThat(touch.get("positionTitle").isNull()).isTrue();
        JsonNode elsewhere = approach.at("/elsewhere/0");
        assertThat(elsewhere.get("visible").asBoolean()).isFalse();
        assertThat(elsewhere.get("inProcess").asBoolean()).isTrue();
        assertThat(elsewhere.get("stageLabel").isNull()).isFalse();
        assertThat(elsewhere.get("positionTitle").isNull()).isTrue();
        assertThat(elsewhere.get("statusLabel").isNull()).isTrue();
        assertThat(elsewhere.get("addedByName").isNull()).isTrue();
        assertThat(approach.toString()).doesNotContain("Group CFO").doesNotContain(cfoSearch);
    }

    @Test
    @DisplayName("a reply leads, and a logged LinkedIn message counts as an approach")
    void aReplyLeadsAndLoggedTouchesCount() throws Exception {
        email(saraId, creditRiskSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(12));
        email(saraId, creditRiskSearch, "INBOUND", "REPLY", "RECEIVED", Duration.ofDays(9));
        as(sara, post("/api/v1/projects/" + creditRiskSearch + "/candidates/" + laylaOnCreditRisk + "/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind":"linkedin","body":"Connected and sent a short note.","occurredAt":"%s"}"""
                        .formatted(Instant.now().minus(Duration.ofDays(3)))))
                .andExpect(status().isCreated());

        JsonNode approach = composerApproach();
        assertThat(approach.at("/touches/0/kind").asText()).isEqualTo("REPLIED");
        assertThat(approach.at("/touches/1/kind").asText()).isEqualTo("LINKEDIN_LOGGED");
        assertThat(approach.at("/touches/2/kind").asText()).isEqualTo("EMAILED");
        assertThat(approach.toString()).doesNotContain("short note");
    }

    @Test
    @DisplayName("the caller's own email on this position is their conversation, not a warning; a teammate's is")
    void ownEmailHereIsLeftOut() throws Exception {
        email(yaraId, cfoSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(2));
        assertThat(composerApproach().get("touches")).isEmpty();

        email(saraId, cfoSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(1));
        JsonNode touch = composerApproach().at("/touches/0");
        assertThat(touch.get("byName").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(touch.get("samePosition").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("an approach older than the window is the last approach, and a window of 0 says nothing")
    void theWindowDecidesWhatIsRecent() throws Exception {
        email(saraId, creditRiskSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(12));
        changeWindow(yara, 10).andExpect(status().isOk());

        JsonNode approach = composerApproach();
        assertThat(approach.get("touches")).isEmpty();
        assertThat(approach.at("/lastApproach/kind").asText()).isEqualTo("EMAILED");
        assertThat(approach.get("tier").asText()).isEqualTo("CAUTION");

        changeWindow(yara, 0).andExpect(status().isOk());
        approach = composerApproach();
        assertThat(approach.get("tier").asText()).isEqualTo("NONE");
        assertThat(approach.get("elsewhere")).isEmpty();
    }

    @Test
    @DisplayName("out of process elsewhere is a quiet line, and an archived position says nothing")
    void outOfProcessElsewhereIsQuiet() throws Exception {
        jdbc.update("""
                update app_lm_project_candidate set status_id = (
                    select id from app_lm_workspace_candidate_status
                    where workspace_id = ?::uuid and key = 'notInterested')
                where id = ?::uuid""", workspaceId, laylaOnCreditRisk);
        JsonNode approach = composerApproach();
        assertThat(approach.get("tier").asText()).isEqualTo("INFO");
        assertThat(approach.at("/elsewhere/0/inProcess").asBoolean()).isFalse();

        jdbc.update("update app_lm_project set archived_at = now(), archived_by = ?::uuid where id = ?::uuid", saraId,
                creditRiskSearch);
        assertThat(composerApproach().get("tier").asText()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("only an admin changes the window, inside 0 to 365 days")
    void theWindowIsAnAdminsInRange() throws Exception {
        changeWindow(sara, 30).andExpect(status().isForbidden());
        changeWindow(yara, 366).andExpect(status().isBadRequest());
        JsonNode workspace = body(changeWindow(yara, 30).andExpect(status().isOk()).andReturn());
        assertThat(workspace.get("recentlyApproachedDays").asInt()).isEqualTo(30);
    }

    private JsonNode composerApproach() throws Exception {
        return body(as(yara, get("/api/v1/projects/" + cfoSearch + "/outreach/conversations/" + laylaOnCfo))
                .andExpect(status().isOk()).andReturn()).at("/composer/recentApproach");
    }

    private JsonNode enrolRow() throws Exception {
        return body(as(yara, post("/api/v1/projects/" + cfoSearch + "/outreach/enrollment-candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[\"%s\"]}".formatted(laylaOnCfo)))
                .andExpect(status().isOk()).andReturn()).at("/people/0");
    }

    private ResultActions changeWindow(String token, int days) throws Exception {
        return as(token, put("/api/v1/workspace/recently-approached").contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":%d}".formatted(days)));
    }

    private void email(String senderId, String projectId, String direction, String kind, String status,
                       Duration ago) {
        Timestamp at = Timestamp.from(Instant.now().minus(ago));
        String candidateId = projectId.equals(cfoSearch) ? laylaOnCfo : laylaOnCreditRisk;
        jdbc.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, sender_user_id,
                    direction, kind, status, subject, body, body_text, provider_message_id, sent_at, occurred_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, 'Confidential introduction',
                    'Confidential introduction', 'Confidential introduction', ?, ?, ?)""",
                workspaceId, projectId, laylaPersonId, candidateId, senderId, direction, kind, status,
                "msg-" + UUID.randomUUID(), "SENT".equals(status) ? at : null, at);
    }

    private String executive(String token, String projectId, String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + emailAddress.hashCode();
        return body(as(token, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
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

    private String userIdOf(String token) throws Exception {
        return body(as(token, get("/api/v1/auth/me")).andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
