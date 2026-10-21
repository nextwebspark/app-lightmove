package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * The Outreach page's queue and one executive's conversation (docs/outreach-conversations.md): the groups read off
 * open tasks and runs, the tabs, Mine and Team, the search over names and emails, the history merged by time, and
 * who may read any of it. The clock is the test's, as in {@code OutreachDispatchIntegrationTest}.
 */
@IntegrationTest
class OutreachConversationIntegrationTest extends FlowTestSupport {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final String STEP_ONE_BODY = "Hi {{firstName}},\n\n{{opener}}\n\nWe are hiring a {{positionTitle}}.";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String consultantId;
    private String projectId;
    private String clientId;
    private Instant monday;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        // The suite shares one database: runs other classes left due would otherwise go out in these passes.
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        String consultantEmail = "yara@" + domain;
        // Its own name: the suite shares one database, and a name runs out of free slugs after 99 workspaces.
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Conversation Partners");
        consultant = login(consultantEmail);
        consultantId = userIdOf(consultant);
        clientId = ownClient(consultant);
        projectId = mandate(consultant, "Group CFO");
        connectMailbox();
        monday = LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(10, 0)).atZone(DUBAI).toInstant();
    }

    @Test
    @DisplayName("the queue groups people by what is open on them: a reply, a follow-up, a first approach, a bounce")
    void theQueueGroupsByWhatIsOpen() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        String omar = executive("Omar Farouk", "omar@" + domain);
        String layla = executive("Layla Haddad", "layla@" + domain);
        String hana = executive("Hana Aziz", "hana@" + domain);
        start(sequenceId, List.of(person(priya, "priya@" + domain, "Your treasury work stood out."),
                person(rajesh, "rajesh@" + domain, null), person(omar, "omar@" + domain, null)));
        dispatcher.dispatchAt(monday);
        reply(priya, "priya@" + domain);
        reply(rajesh, "mailer-daemon@" + domain);
        task(omar, "Call Omar after his board meeting", consultantId, monday.plus(Duration.ofDays(1)));
        task(layla, "Find Layla's work email", consultantId, monday.plus(Duration.ofDays(2)));

        JsonNode needs = conversations();
        assertThat(candidateIdsOf(needs)).containsExactly(priya, omar, layla, rajesh);
        assertThat(groupsOf(needs)).containsExactly("NEEDS_REPLY", "FOLLOW_UP", "PREPARATION", "ATTENTION");
        assertThat(rowOf(needs, priya).at("/action/title").asText()).isEqualTo("Reply to Priya Raman");
        assertThat(rowOf(needs, priya).at("/action/rule").asText()).isEqualTo("reply");
        assertThat(rowOf(needs, priya).at("/run/status").asText()).isEqualTo("REPLIED");
        assertThat(rowOf(needs, priya).at("/latest/preview").asText()).startsWith("Hi Priya, Your treasury work");
        assertThat(rowOf(needs, rajesh).at("/action/title").asText()).isEqualTo("Find a working email for Rajesh Menon");
        assertThat(rowOf(needs, layla).get("run").isNull()).isTrue();
        assertThat(needs.at("/counts/needs").asInt()).isEqualTo(4);
        assertThat(needs.at("/counts/scheduled").asInt()).isEqualTo(1);
        assertThat(needs.get("nextScheduledAt").isNull()).isFalse();

        assertThat(candidateIdsOf(conversations("view", "all"))).containsExactlyInAnyOrder(priya, rajesh, omar, layla)
                .doesNotContain(hana);
        assertThat(candidateIdsOf(conversations("view", "scheduled"))).containsExactly(omar);
        assertThat(candidateIdsOf(conversations("view", "sent"))).containsExactlyInAnyOrder(priya, rajesh, omar);
    }

    @Test
    @DisplayName("a run a failed send stopped needs attention with no task, until something goes to them again")
    void aFailedSendNeedsAttention() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), List.of(person(priya, "priya@" + domain, null)));
        gateway.failSendsWith(new IllegalStateException("refused"));
        dispatcher.dispatchAt(monday);
        gateway.failSendsWith(null);

        JsonNode row = rowOf(conversations(), priya);
        assertThat(row.get("group").asText()).isEqualTo("ATTENTION");
        assertThat(row.get("action").isNull()).isTrue();
        assertThat(row.at("/run/stopReason").asText()).isEqualTo("SEND_FAILED");
    }

    @Test
    @DisplayName("Mine is what the caller holds or sent; Team is the whole position")
    void mineAndTeam() throws Exception {
        inviteAndAccept(consultant, "Sara Al-Mansour", "sara@" + domain, "ADMIN");
        String saraId = userIdOf(login("sara@" + domain));
        String priya = executive("Priya Raman", "priya@" + domain);
        String layla = executive("Layla Haddad", "layla@" + domain);
        task(priya, "Call Priya", consultantId, monday);
        task(layla, "Call Layla", saraId, monday);

        assertThat(candidateIdsOf(conversations("who", "mine"))).containsExactly(priya);
        assertThat(conversations("who", "mine").at("/counts/needs").asInt()).isEqualTo(1);
        assertThat(candidateIdsOf(conversations("who", "team"))).containsExactlyInAnyOrder(priya, layla);
        assertThat(conversations("who", "team").at("/counts/needs").asInt()).isEqualTo(2);
        as(consultant, get(outreach("/conversations?who=everyone"))).andExpect(status().isBadRequest());
        as(consultant, get(outreach("/conversations?view=inbox"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the search finds people by name, title or employer, and by the words of what was sent to them")
    void theSearchReadsNamesAndEmails() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(createSequence("First approach"), List.of(person(priya, "priya@" + domain, "Your treasury work stood out."),
                person(rajesh, "rajesh@" + domain, null)));
        dispatcher.dispatchAt(monday);

        assertThat(candidateIdsOf(conversations("view", "all", "q", "RAJESH"))).containsExactly(rajesh);
        assertThat(candidateIdsOf(conversations("view", "all", "q", "treasury work"))).containsExactly(priya);
        assertThat(candidateIdsOf(conversations("view", "all", "q", "Target Group")))
                .containsExactlyInAnyOrder(priya, rajesh);
        // Unescaped, a bare % or _ would match everyone.
        assertThat(candidateIdsOf(conversations("view", "all", "q", "%"))).isEmpty();
        assertThat(candidateIdsOf(conversations("view", "all", "q", "_"))).isEmpty();
        as(consultant, get(outreach("/conversations")).param("view", "all").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a conversation is the emails, run events, this position's notes and meetings, newest first")
    void aConversationMergesItsHistory() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), List.of(person(priya, "priya@" + domain, "Your treasury work stood out.")));
        dispatcher.dispatchAt(monday);
        reply(priya, "priya@" + domain);
        note(projectId, priya, "call", "Brief call; she wants the mandate scope first.");
        String otherProject = mandate(consultant, "Group Treasurer");
        String priyaThere = executive(otherProject, "Priya Raman", "priya@" + domain);
        note(otherProject, priyaThere, "general", "Treasurer search: not for her.");

        JsonNode conversation = body(as(consultant, get(outreach("/conversations/" + priya)))
                .andExpect(status().isOk()).andReturn());

        assertThat(conversation.at("/executive/fullName").asText()).isEqualTo("Priya Raman");
        assertThat(conversation.at("/openTasks/0/title").asText()).isEqualTo("Reply to Priya Raman");
        assertThat(conversation.at("/currentRun/run/status").asText()).isEqualTo("REPLIED");
        assertThat(conversation.at("/keyDates/firstContactedAt").asText()).isEqualTo(monday.toString());
        assertThat(conversation.at("/keyDates/repliedAt").isNull()).isFalse();
        assertThat(conversation.get("truncated").asBoolean()).isFalse();

        List<JsonNode> events = new ArrayList<>();
        conversation.get("events").forEach(events::add);
        assertThat(events).extracting(event -> event.get("type").asText())
                .contains("MESSAGE", "ACTIVITY", "NOTE");
        for (int i = 1; i < events.size(); i++) {
            assertThat(Instant.parse(events.get(i - 1).get("at").asText()))
                    .isAfterOrEqualTo(Instant.parse(events.get(i).get("at").asText()));
        }
        JsonNode email = events.stream().filter(event -> event.get("type").asText().equals("MESSAGE")).findFirst()
                .orElseThrow().get("message");
        assertThat(email.get("direction").asText()).isEqualTo("OUTBOUND");
        assertThat(email.get("kind").asText()).isEqualTo("SEQUENCE_STEP");
        assertThat(email.get("subject").asText()).isEqualTo("Confidential: Group CFO");
        assertThat(email.get("text").asText())
                .isEqualTo("Hi Priya,\n\nYour treasury work stood out.\n\nWe are hiring a Group CFO.");
        assertThat(email.get("toAddress").asText()).isEqualTo("priya@" + domain);
        assertThat(email.get("senderName").asText()).isEqualTo("Yara Haddad");
        assertThat(email.get("sequenceName").asText()).isEqualTo("First approach");
        assertThat(email.get("stepNumber").asInt()).isEqualTo(1);
        assertThat(email.get("stepCount").asInt()).isEqualTo(3);
        assertThat(events).extracting(event -> event.at("/activity/kind").asText(""))
                .contains("OUTREACH_ENROLLED", "EMAIL_REPLIED")
                .doesNotContain("EMAIL_SENT");
        assertThat(events).extracting(event -> event.at("/note/body").asText(""))
                .contains("Brief call; she wants the mandate scope first.")
                .doesNotContain("Treasurer search: not for her.");
    }

    @Test
    @DisplayName("an email's timeline line names its sequence and step, as the drawer's Activity reads it")
    void theTimelineNamesTheSequenceAndStep() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), List.of(person(priya, "priya@" + domain, null)));
        dispatcher.dispatchAt(monday);

        JsonNode timeline = body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + priya
                + "/timeline")).andExpect(status().isOk()).andReturn());
        JsonNode sent = null;
        for (JsonNode entry : timeline.get("entries")) {
            if (entry.get("kind").asText().equals("EMAIL_SENT")) {
                sent = entry;
            }
        }
        assertThat(sent).isNotNull();
        assertThat(sent.at("/details/sequence").asText()).isEqualTo("First approach");
        assertThat(sent.at("/details/step").asText()).isEqualTo("1");
    }

    @Test
    @DisplayName("a client seat is refused both reads, and another workspace or position finds nothing")
    void onlyThePositionsTeamReads() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        String rep = clientSeat();
        as(rep, get(outreach("/conversations"))).andExpect(status().isForbidden());
        as(rep, get(outreach("/conversations/" + priya))).andExpect(status().isForbidden());

        String otherProject = mandate(consultant, "Group Treasurer");
        as(consultant, get("/api/v1/projects/" + otherProject + "/outreach/conversations/" + priya))
                .andExpect(status().isNotFound());

        String outsiderEmail = "omar@other-" + domain;
        createWorkspace(verifiedUser("Omar Farouk", outsiderEmail), "Conversation Outsiders");
        String outsider = login(outsiderEmail);
        as(outsider, get(outreach("/conversations"))).andExpect(status().isNotFound());
        as(outsider, get(outreach("/conversations/" + priya))).andExpect(status().isNotFound());
    }

    /** {@code params} are name, value pairs, sent as request parameters so the test spells them as typed. */
    private JsonNode conversations(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(outreach("/conversations"));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return body(as(consultant, request).andExpect(status().isOk()).andReturn());
    }

    private static List<String> candidateIdsOf(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("rows").forEach(row -> ids.add(row.get("candidateId").asText()));
        return ids;
    }

    private static List<String> groupsOf(JsonNode page) {
        List<String> groups = new ArrayList<>();
        page.get("rows").forEach(row -> groups.add(row.get("group").asText()));
        return groups;
    }

    private static JsonNode rowOf(JsonNode page, String candidateId) {
        for (JsonNode row : page.get("rows")) {
            if (candidateId.equals(row.get("candidateId").asText())) {
                return row;
            }
        }
        throw new AssertionError("No conversation row for " + candidateId);
    }

    private void reply(String candidateId, String fromAddress) throws Exception {
        String thread = (String) jdbc.queryForMap("select thread_id from app_lm_outreach_enrollment "
                + "where candidate_id = ?::uuid", candidateId).get("thread_id");
        gateway.deliverNext(List.of(new InboundMessage("grant-1", thread, fromAddress)));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private void task(String candidateId, String title, String assigneeId, Instant dueAt) throws Exception {
        as(consultant, post("/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"%s","kind":"call","assigneeUserId":"%s","dueAt":"%s"}"""
                        .formatted(title, assigneeId, dueAt)))
                .andExpect(status().isCreated());
    }

    private void note(String inProject, String candidateId, String kind, String text) throws Exception {
        as(consultant, post("/api/v1/projects/" + inProject + "/candidates/" + candidateId + "/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind":"%s","body":"%s"}""".formatted(kind, text)))
                .andExpect(status().isCreated());
    }

    private void start(String sequenceId, List<String> people) throws Exception {
        as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"people\":[" + String.join(",", people) + "]}"))
                .andExpect(status().isCreated());
    }

    private static String person(String candidateId, String toAddress, String opener) {
        return """
                {"candidateId":"%s","toAddress":"%s","opener":%s,"openerEdited":false}"""
                .formatted(candidateId, toAddress, opener == null ? "null" : "\"" + opener + "\"");
    }

    private String createSequence(String name) throws Exception {
        String steps = String.join(",", List.of(
                """
                {"delayWorkingDays":0,"subject":"Confidential: {{positionTitle}}","body":"%s"}"""
                        .formatted(STEP_ONE_BODY.replace("\n", "\\n")),
                """
                {"delayWorkingDays":3,"body":"Following up, {{firstName}}."}""",
                """
                {"delayWorkingDays":2,"body":"One last note, {{firstName}}."}"""));
        return body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"steps\":[%s]}".formatted(name, steps)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String executive(String fullName, String emailAddress) throws Exception {
        return executive(projectId, fullName, emailAddress);
    }

    private String executive(String inProject, String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + emailAddress.hashCode();
        return body(as(consultant, post("/api/v1/projects/" + inProject + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate(String token, String title) throws Exception {
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

    private String clientSeat() throws Exception {
        String repEmail = "rep@client-" + domain;
        String representativeId = body(as(consultant, post("/api/v1/clients/" + clientId + "/representatives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Rep Person","position":"Advisor","email":"%s"}""".formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Rep Person","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        as(consultant, post("/api/v1/projects/" + projectId + "/representatives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"representativeId\":\"%s\"}".formatted(representativeId)))
                .andExpect(status().is2xxSuccessful());
        return rep;
    }

    private void connectMailbox() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", state).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private String userIdOf(String token) throws Exception {
        return body(as(token, get("/api/v1/auth/me")).andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }
}
