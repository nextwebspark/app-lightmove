package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingMailboxGateway.SentRecord;
import app.lightmove.api.outreach.model.BookingMade;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
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
 * The booking link: a sequence carrying {@code {{bookingLink}}} makes the sender's page at Start and sends
 * the link as an anchor; the public page opens it with no session; and a booking through it ends the
 * executive's run before its next step and moves them forward to Engaged.
 */
@IntegrationTest
class BookingLinkIntegrationTest extends FlowTestSupport {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final String MAILBOX = "consultant@firm.example";
    private static final String STEP_ONE_BODY = "Hi {{firstName}},\n\nPick a time that suits you: {{bookingLink}}";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
    private String workspaceId;
    private Instant monday;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        // The suite shares one database: runs other classes left due would otherwise go out in these passes.
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Search");
        consultant = login(consultantEmail);
        projectId = mandate();
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
        monday = LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(10, 0)).atZone(DUBAI).toInstant();
    }

    @Test
    @DisplayName("Start makes the sender's page, and the first email carries the link as its one anchor")
    void startMakesThePageAndTheEmailCarriesTheLink() throws Exception {
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);
        startSequence(createSequence(), priya, "priya@" + domain);

        assertThat(gateway.bookingPages()).hasSize(1);
        BookingPageSpec page = gateway.bookingPages().getFirst();
        assertThat(page.minutes()).isEqualTo(30);
        assertThat(page.organizerAddress()).isEqualTo(MAILBOX);
        assertThat(page.zone()).isEqualTo(DUBAI);
        assertThat(page.eventTitle()).isEqualTo("30-minute call with Yara Haddad");
        Map<String, Object> mailbox = mailboxRow();
        String slug = (String) mailbox.get("booking_slug");
        assertThat(slug).startsWith("yara-haddad");
        assertThat(mailbox.get("booking_configuration_id")).isEqualTo("booking-page-1");

        dispatcher.dispatchAt(monday);

        String link = "http://localhost:5173/book/" + slug;
        SentRecord first = sentTo("priya@" + domain).getFirst();
        assertThat(first.email().htmlBody())
                .isEqualTo("<p>Hi Priya,</p><p>Pick a time that suits you: <a href=\"" + link + "\">" + link + "</a></p>");

        // A second Start reuses the page rather than making another.
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        startSequence(createSequence(), rajesh, "rajesh@" + domain);
        assertThat(gateway.bookingPages()).hasSize(1);
    }

    @Test
    @DisplayName("the public page opens with no session; an unknown, malformed or withdrawn link finds nothing")
    void thePublicPageOpensTheConsultantsCalendar() throws Exception {
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);
        startSequence(createSequence(), priya, "priya@" + domain);
        String slug = (String) mailboxRow().get("booking_slug");

        JsonNode page = body(mvc.perform(get("/api/v1/outreach/booking/" + slug)).andExpect(status().isOk()).andReturn());
        assertThat(page.get("configurationId").asText()).isEqualTo("booking-page-1");
        assertThat(page.get("consultantName").asText()).isEqualTo("Yara Haddad");
        assertThat(page.get("schedulerApiUrl").asText()).startsWith("https://");

        mvc.perform(get("/api/v1/outreach/booking/nobody-at-all")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/outreach/booking/Not_A_Slug")).andExpect(status().isNotFound());

        // A reconnect brings a new grant, so the page is made again the next time someone opens the link.
        gateway.grant(new GrantedMailbox(grantId + "-2", MAILBOX, "google"));
        connectMailbox();
        assertThat(mailboxRow().get("booking_configuration_id")).isNull();
        JsonNode reopened = body(mvc.perform(get("/api/v1/outreach/booking/" + slug)).andExpect(status().isOk())
                .andReturn());
        assertThat(reopened.get("configurationId").asText()).isEqualTo("booking-page-2");

        gateway.offerBookingPages(false);
        mvc.perform(get("/api/v1/outreach/booking/" + slug)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a booking through the link stops the sequence before its next step and moves them to Engaged")
    void aBookingThroughTheLinkStopsTheSequence() throws Exception {
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);
        startSequence(createSequence(), priya, "priya@" + domain);
        dispatcher.dispatchAt(monday);
        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(candidateStatus(priya)).isEqualTo("contacted");

        Instant call = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(2));
        gateway.deliverNext(List.of(new BookingMade(null, "booking-page-1", "booked-evt-1",
                "30-minute call with Yara Haddad", call, call.plus(Duration.ofMinutes(30)),
                List.of(MAILBOX, "Priya@" + domain))));
        webhook();

        Map<String, Object> run = jdbc.queryForMap(
                "select status, booked_via_link from app_lm_outreach_enrollment where candidate_id = ?::uuid", priya);
        assertThat(run.get("status")).isEqualTo("BOOKED");
        assertThat(run.get("booked_via_link")).isEqualTo(true);
        assertThat(candidateStatus(priya)).isEqualTo("engaged");
        assertThat(activityKinds(priya)).contains("MEETING_BOOKED", "OUTREACH_STOPPED");

        JsonNode meetings = body(as(consultant, get(outreach("/candidates/" + priya + "/meetings"))).andReturn());
        assertThat(meetings.get("upcoming")).hasSize(1);
        assertThat(meetings.get("upcoming").get(0).get("viaLink").asBoolean()).isTrue();

        JsonNode drawer = body(as(consultant, get(outreach("/candidates/" + priya))).andReturn());
        assertThat(drawer.get("run").get("bookedViaLink").asBoolean()).isTrue();

        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));
        assertThat(sentTo("priya@" + domain)).hasSize(1);
    }

    @Test
    @DisplayName("a booking by someone the workspace does not hold touches nothing")
    void aStrangersBookingTouchesNothing() throws Exception {
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);
        startSequence(createSequence(), priya, "priya@" + domain);

        Instant call = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(2));
        gateway.deliverNext(List.of(new BookingMade(null, "booking-page-1", "booked-evt-2", "Call", call,
                call.plus(Duration.ofMinutes(30)), List.of(MAILBOX, "someone@elsewhere.example"))));
        webhook();

        assertThat(jdbc.queryForObject("select status from app_lm_outreach_enrollment where candidate_id = ?::uuid",
                String.class, priya)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_person_meeting where workspace_id = ?::uuid",
                Integer.class, workspaceId)).isZero();
    }

    @Test
    @DisplayName("where the plan has no booking pages, a sequence cannot carry the link and the mailbox says so")
    void unofferedLinksAreRefused() throws Exception {
        gateway.offerBookingPages(false);
        connectMailbox();

        assertThat(body(as(consultant, get("/api/v1/outreach/mailbox")).andReturn()).get("bookingLinkOffered")
                .asBoolean()).isFalse();
        assertThat(codeOf(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                        .content(sequenceBody())).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("OUTREACH_BOOKING_LINK_UNAVAILABLE");

        gateway.offerBookingPages(true);
        assertThat(body(as(consultant, get("/api/v1/outreach/mailbox")).andReturn()).get("bookingLinkOffered")
                .asBoolean()).isTrue();
    }

    private Map<String, Object> mailboxRow() {
        return jdbc.queryForMap("select booking_slug, booking_configuration_id from app_lm_mailbox_connection "
                + "where workspace_id = ?::uuid", workspaceId);
    }

    private List<SentRecord> sentTo(String address) {
        return gateway.sent().stream().filter(record -> record.email().to().equalsIgnoreCase(address)).toList();
    }

    private void webhook() throws Exception {
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private List<String> activityKinds(String candidateId) {
        return jdbc.queryForList("select a.kind from app_lm_person_activity a join app_lm_project_candidate c "
                + "on c.person_id = a.person_id where c.id = ?::uuid order by a.occurred_at, a.id", String.class, candidateId);
    }

    private String candidateStatus(String candidateId) throws Exception {
        return body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId)).andReturn())
                .get("status").asText();
    }

    private static String sequenceBody() {
        return """
                {"name":"First approach","steps":[
                  {"delayWorkingDays":0,"subject":"Confidential: {{positionTitle}}","body":"%s"},
                  {"delayWorkingDays":3,"body":"Following up, {{firstName}}."}]}
                """.formatted(STEP_ONE_BODY.replace("\n", "\\n"));
    }

    private String createSequence() throws Exception {
        return body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content(sequenceBody()))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private void startSequence(String sequenceId, String candidateId, String address) throws Exception {
        as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"people":[{"candidateId":"%s","toAddress":"%s","opener":null,"openerEdited":false}]}
                        """.formatted(candidateId, address)))
                .andExpect(status().isCreated());
    }

    private String executive(String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + System.nanoTime();
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate() throws Exception {
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
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

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }
}
