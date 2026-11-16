package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
    @DisplayName("a client seat on the other position does not open it: it stays unnamed, and never says placed")
    void aClientSeatElsewhereKeepsItUnnamed() throws Exception {
        jdbc.update("""
                with seat as (
                    insert into app_lm_project_member (project_id, member_id, added_by)
                    select ?::uuid, id, ?::uuid from app_lm_workspace_member
                    where user_id = ?::uuid and workspace_id = ?::uuid
                    returning id)
                insert into app_lm_project_member_role (project_member_id, role_id)
                select seat.id, role.id from seat, app_lm_role role where role.name = 'CLIENT' and role.scope = 'PROJECT'
                """, cfoSearch, yaraId, saraId, workspaceId);
        moveTo(laylaOnCfo, "placed");
        email(yaraId, cfoSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(4));

        JsonNode approach = body(as(sara, get("/api/v1/projects/" + creditRiskSearch + "/outreach/conversations/"
                        + laylaOnCreditRisk)).andExpect(status().isOk()).andReturn()).at("/composer/recentApproach");
        assertThat(approach.at("/touches/0/positionHidden").asBoolean()).isTrue();
        JsonNode elsewhere = approach.at("/elsewhere/0");
        assertThat(elsewhere.get("visible").asBoolean()).isFalse();
        assertThat(elsewhere.get("inProcess").asBoolean()).isTrue();
        assertThat(elsewhere.get("placed").asBoolean()).isFalse();
        assertThat(approach.toString()).doesNotContain("Group CFO").doesNotContain(cfoSearch);
    }

    @Test
    @DisplayName("a retired status that has not left the running is still in process elsewhere")
    void aRetiredStatusIsStillInProcess() throws Exception {
        jdbc.update("""
                insert into app_lm_workspace_candidate_status (workspace_id, stage_id, key, label, position)
                select workspace_id, stage_id, 'heldForLater', 'Held for later', 99
                from app_lm_workspace_candidate_status where workspace_id = ?::uuid and key = 'interested'
                """, workspaceId);
        moveTo(laylaOnCreditRisk, "heldForLater");
        jdbc.update("""
                update app_lm_workspace_candidate_status set retired_at = now()
                where workspace_id = ?::uuid and key = 'heldForLater'""", workspaceId);

        JsonNode approach = composerApproach();
        assertThat(approach.get("tier").asText()).isEqualTo("CAUTION");
        assertThat(approach.at("/elsewhere/0/inProcess").asBoolean()).isTrue();
        assertThat(approach.at("/elsewhere/0/statusLabel").asText()).isEqualTo("Held for later");
    }

    @Test
    @DisplayName("the caller's own call logged on no position is already in their conversation")
    void ownLogWithNoPositionIsLeftOut() throws Exception {
        jdbc.update("""
                insert into app_lm_person_note (workspace_id, person_id, kind, body, author_user_id, occurred_at)
                values (?::uuid, ?::uuid, 'CALL', 'Called from the pool', ?::uuid, now() - interval '2 days')
                """, workspaceId, laylaPersonId, yaraId);
        assertThat(composerApproach().get("touches")).isEmpty();
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
    @DisplayName("a teammate's many emails are one line, and never crowd the rest of the team out")
    void manyEmailsFromOneTeammateAreOneLine() throws Exception {
        for (int day = 1; day <= 12; day++) {
            email(saraId, creditRiskSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(day));
        }
        loggedNote(yaraId, creditRiskSearch, "LINKEDIN", Duration.ofDays(20));

        JsonNode touches = composerApproach().get("touches");
        assertThat(touches).hasSize(2);
        assertThat(touches.at("/0/kind").asText()).isEqualTo("EMAILED");
        assertThat(touches.at("/0/byName").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(touches.at("/1/kind").asText()).isEqualTo("LINKEDIN_LOGGED");
        assertThat(touches.at("/1/byYou").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a teammate's email on an archived position still warns, though the mapping there says nothing")
    void anArchivedPositionsEmailStillWarns() throws Exception {
        email(saraId, creditRiskSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(5));
        jdbc.update("update app_lm_project set archived_at = now(), archived_by = ?::uuid where id = ?::uuid", saraId,
                creditRiskSearch);

        JsonNode approach = composerApproach();
        assertThat(approach.get("tier").asText()).isEqualTo("CAUTION");
        assertThat(approach.at("/touches/0/positionTitle").asText()).isEqualTo("Head of Credit Risk");
        assertThat(approach.get("elsewhere")).isEmpty();
    }

    @Test
    @DisplayName("placed elsewhere reads placed, under whatever label the workspace gave Placed")
    void aRenamedPlacedStillReadsPlaced() throws Exception {
        moveTo(laylaOnCreditRisk, "placed");
        jdbc.update("update app_lm_workspace_candidate_status set label = 'Hired' where workspace_id = ?::uuid "
                + "and key = 'placed'", workspaceId);

        JsonNode position = composerApproach().at("/elsewhere/0");
        assertThat(position.get("placed").asBoolean()).isTrue();
        assertThat(position.get("statusLabel").asText()).isEqualTo("Hired");
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
        loggedNote(saraId, creditRiskSearch, "CALL", Duration.ofDays(30));
        changeWindow(yara, 10).andExpect(status().isOk());
        assertThat(composerApproach().at("/lastApproach/kind").asText()).isEqualTo("CALL_LOGGED");

        email(saraId, creditRiskSearch, "OUTBOUND", "MANUAL", "SENT", Duration.ofDays(12));
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
        moveTo(laylaOnCreditRisk, "notInterested");
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

    @Test
    @DisplayName("a call that rang out or reached a wrong number warns nobody, before the window or in it")
    void anUnansweredCallIsNoApproach() throws Exception {
        loggedOutcome(saraId, creditRiskSearch, "CALL", "VOICEMAIL", Duration.ofDays(40));
        loggedOutcome(saraId, creditRiskSearch, "CALL", "NO_ANSWER", Duration.ofDays(35));
        loggedOutcome(saraId, creditRiskSearch, "CALL", "NO_ANSWER", Duration.ofDays(3));
        loggedOutcome(saraId, creditRiskSearch, "CALL", "WRONG_NUMBER", Duration.ofDays(1));
        changeWindow(yara, 30).andExpect(status().isOk());

        JsonNode approach = composerApproach();
        assertThat(approach.get("touches")).isEmpty();
        assertThat(approach.at("/lastApproach/kind").asText()).isEqualTo("CALL_LOGGED");
        assertThat(approach.at("/lastApproach/outcome").asText()).isEqualTo("voicemail");
    }

    @Test
    @DisplayName("a connected call names how it went, and a logged WhatsApp reply ranks as their reply")
    void loggedOutcomesNameTheTouch() throws Exception {
        loggedOutcome(saraId, creditRiskSearch, "CALL", "CONNECTED", Duration.ofDays(4));
        loggedOutcome(yaraId, creditRiskSearch, "WHATSAPP", "REPLIED", Duration.ofDays(6));

        JsonNode touches = composerApproach().get("touches");
        assertThat(touches.at("/0/kind").asText()).isEqualTo("REPLIED");
        assertThat(touches.at("/0/outcome").asText()).isEqualTo("replied");
        assertThat(touches.at("/1/kind").asText()).isEqualTo("CALL_LOGGED");
        assertThat(touches.at("/1/outcome").asText()).isEqualTo("connected");
    }

    @Test
    @DisplayName("an emailed reply and a WhatsApp one to the same teammate on the same position are two lines")
    void anEmailedAndAWhatsAppReplyAreTwoLines() throws Exception {
        email(saraId, creditRiskSearch, "INBOUND", "REPLY", "RECEIVED", Duration.ofDays(2));
        loggedOutcome(saraId, creditRiskSearch, "WHATSAPP", "REPLIED", Duration.ofDays(5));

        JsonNode touches = composerApproach().get("touches");
        assertThat(touches).hasSize(2);
        assertThat(touches.at("/0/kind").asText()).isEqualTo("REPLIED");
        assertThat(touches.at("/0/outcome").isNull()).isTrue();
        assertThat(touches.at("/1/kind").asText()).isEqualTo("REPLIED");
        assertThat(touches.at("/1/outcome").asText()).isEqualTo("replied");
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

    private void moveTo(String candidateId, String statusKey) {
        jdbc.update("""
                update app_lm_project_candidate set status_id = (
                    select id from app_lm_workspace_candidate_status where workspace_id = ?::uuid and key = ?)
                where id = ?::uuid""", workspaceId, statusKey, candidateId);
    }

    private ResultActions changeWindow(String token, int days) throws Exception {
        return as(token, put("/api/v1/workspace/recently-approached").contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":%d}".formatted(days)));
    }

    private void loggedNote(String authorId, String projectId, String kind, Duration ago) {
        jdbc.update("""
                insert into app_lm_person_note (workspace_id, person_id, project_id, kind, body, author_user_id,
                    occurred_at)
                values (?::uuid, ?::uuid, ?::uuid, ?, 'Spoke about the move', ?::uuid, ?)""",
                workspaceId, laylaPersonId, projectId, kind, authorId, Timestamp.from(Instant.now().minus(ago)));
    }

    private void loggedOutcome(String authorId, String projectId, String kind, String outcome, Duration ago) {
        jdbc.update("""
                insert into app_lm_person_note (workspace_id, person_id, project_id, kind, outcome, body,
                    author_user_id, occurred_at)
                values (?::uuid, ?::uuid, ?::uuid, ?, ?, '', ?::uuid, ?)""",
                workspaceId, laylaPersonId, projectId, kind, outcome, authorId,
                Timestamp.from(Instant.now().minus(ago)));
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
