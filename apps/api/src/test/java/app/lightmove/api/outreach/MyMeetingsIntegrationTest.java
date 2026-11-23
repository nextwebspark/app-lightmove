package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/** Home's Today: the caller's own meetings between their local midnights, each placed on a live position of theirs. */
@IntegrationTest
class MyMeetingsIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired @FixtureJdbc private JdbcTemplate jdbc;

    private String consultant;
    private String clientId;
    private String projectId;
    private Instant startOfToday;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        gateway.grant(new GrantedMailbox("grant-" + domain, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Today");
        consultant = login(consultantEmail);
        projectId = mandate();
        startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Test
    @DisplayName("today's meetings come back soonest first, each with the executive, position and client")
    void todaysMeetingsCarryTheirPosition() throws Exception {
        String karimId = executive("Karim Nassar", "karim@" + domain);
        executive("Lina Farouk", "lina@" + domain);
        gateway.calendarHolds(List.of(
                event("later", "Shortlist review", startOfToday.plus(Duration.ofMinutes(20)), "lina@" + domain),
                event("first", "Screening", startOfToday.plus(Duration.ofMinutes(5)), "karim@" + domain),
                event("tomorrow", "Second round", startOfToday.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(5)),
                        "karim@" + domain)));
        connectMailbox();

        JsonNode meetings = today().get("meetings");

        assertThat(meetings).hasSize(2);
        JsonNode first = meetings.get(0);
        assertThat(first.get("title").asText()).isEqualTo("Screening");
        assertThat(first.get("personName").asText()).isEqualTo("Karim Nassar");
        assertThat(first.get("candidateId").asText()).isEqualTo(karimId);
        assertThat(first.get("projectId").asText()).isEqualTo(projectId);
        assertThat(first.get("positionTitle").asText()).isEqualTo("Group CFO");
        assertThat(first.get("clientName").asText()).isEqualTo("Acme Holdings");
        assertThat(first.get("videoProvider").asText()).isEqualTo("Google Meet");
        assertThat(meetings.get(1).get("title").asText()).isEqualTo("Shortlist review");
    }

    @Test
    @DisplayName("a meeting with an executive on no live position of the caller's is listed without one")
    void aMeetingOffEveryLivePositionHasNoPlacement() throws Exception {
        executive("Karim Nassar", "karim@" + domain);
        gateway.calendarHolds(List.of(
                event("first", "Screening", startOfToday.plus(Duration.ofMinutes(5)), "karim@" + domain)));
        connectMailbox();
        jdbc.update("update app_lm_project set archived_at = now(), archived_by = "
                + "(select id from app_lm_user where email = ?) where id = ?::uuid", "yara@" + domain, projectId);

        JsonNode meeting = today().get("meetings").get(0);

        assertThat(meeting.get("personName").asText()).isEqualTo("Karim Nassar");
        assertThat(meeting.get("projectId").isNull()).isTrue();
        assertThat(meeting.get("candidateId").isNull()).isTrue();
        assertThat(meeting.get("clientName").isNull()).isTrue();
    }

    @Test
    @DisplayName("with no calendar connected, today is empty")
    void noCalendarMeansNoMeetings() throws Exception {
        assertThat(today().get("meetings")).isEmpty();
    }

    @Test
    @DisplayName("a client representative finds no such route")
    void aClientRepresentativeIsRefused() throws Exception {
        as(clientSeat(), get("/api/v1/me/meetings/today").param("tz", "UTC")).andExpect(status().isNotFound());
    }

    private JsonNode today() throws Exception {
        return body(as(consultant, get("/api/v1/me/meetings/today").param("tz", "UTC"))
                .andExpect(status().isOk()).andReturn());
    }

    private static CalendarEvent event(String id, String title, Instant start, String executiveAddress) {
        return new CalendarEvent(id, title, start, start.plus(Duration.ofMinutes(30)),
                List.of(MAILBOX, executiveAddress), "https://meet.example/" + id, "Google Meet");
    }

    private String executive(String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + System.nanoTime();
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Buying Director","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate() throws Exception {
        clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
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
                                {"token":"%s","fullName":"Rep Person","password":"%s","termsAccepted":true}
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

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
