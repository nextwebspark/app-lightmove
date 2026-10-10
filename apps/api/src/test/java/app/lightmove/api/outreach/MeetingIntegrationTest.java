package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.CalendarEventRemoved;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.service.MeetingBackfill;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
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
 * Meetings with an executive from the team's calendars, and Book a call, against a recording mailbox:
 * only events with a mapped person's address are kept, the webhook keeps them current, connecting
 * reads the calendar once, and a booked call invites the executive, moves them forward and ends their run.
 */
@IntegrationTest
class MeetingIntegrationTest extends FlowTestSupport {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private MeetingBackfill backfill;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MailboxConnectionRepository mailboxes;

    private String grantId;
    private String consultant;
    private String projectId;
    private String clientId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Search");
        consultant = login(consultantEmail);
        projectId = mandate();
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
    }

    @Test
    @DisplayName("an event with nobody mapped on it is never kept; one with an executive follows the webhook")
    void onlyEventsWithAMappedPersonAreKept() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        connectMailbox();
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(2));

        changed(event("dentist", "Dentist", start, MAILBOX, "front-desk@clinic.example"));
        assertThat(meetingRows()).isZero();

        changed(event("call-1", "First conversation", start, MAILBOX, "Priya@" + domain));
        JsonNode meetings = meetingsOf(priya);
        assertThat(meetings.get("upcoming")).hasSize(1);
        JsonNode upcoming = meetings.get("upcoming").get(0);
        assertThat(upcoming.get("title").asText()).isEqualTo("First conversation");
        assertThat(upcoming.get("ownerName").asText()).isEqualTo("Yara Haddad");
        assertThat(upcoming.get("joinUrl").asText()).isEqualTo("https://meet.example/call-1");
        assertThat(upcoming.get("videoProvider").asText()).isEqualTo("Google Meet");
        assertThat(meetingsOf(rajesh).get("upcoming")).isEmpty();

        // Moved, and Rajesh invited in Priya's place.
        Instant moved = start.plus(Duration.ofHours(1));
        changed(event("call-1", "First conversation", moved, MAILBOX, "rajesh@" + domain));
        assertThat(meetingsOf(priya).get("upcoming")).isEmpty();
        JsonNode rajeshs = meetingsOf(rajesh).get("upcoming");
        assertThat(rajeshs).hasSize(1);
        assertThat(Instant.parse(rajeshs.get(0).get("startsAt").asText())).isEqualTo(moved);

        gateway.deliverNext(List.of(new CalendarEventRemoved(grantId, "call-1")));
        webhook();
        assertThat(meetingRows()).isZero();
    }

    @Test
    @DisplayName("connecting reads the calendar once: upcoming and past, the past without a join link")
    void connectingReadsTheCalendar() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        gateway.calendarHolds(List.of(
                event("past", "Coffee", now.minus(Duration.ofDays(10)), MAILBOX, "priya@" + domain),
                event("next", "Second conversation", now.plus(Duration.ofDays(3)), MAILBOX, "priya@" + domain),
                event("other", "Board prep", now.plus(Duration.ofDays(1)), MAILBOX, "cfo@elsewhere.example")));

        connectMailbox();

        JsonNode meetings = meetingsOf(priya);
        assertThat(meetings.get("upcoming")).hasSize(1);
        assertThat(meetings.get("upcoming").get(0).get("title").asText()).isEqualTo("Second conversation");
        assertThat(meetings.get("past")).hasSize(1);
        assertThat(meetings.get("past").get(0).get("joinUrl").isNull()).isTrue();
        assertThat(meetingRows()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select calendar_synced_at from app_lm_mailbox_connection "
                + "where workspace_id = ?::uuid", Instant.class, workspaceId)).isNotNull();
    }

    @Test
    @DisplayName("of two claims on one stale calendar only the first lands, and a release never undoes a later claim")
    void aCalendarRefreshIsClaimedOnce() throws Exception {
        connectMailbox();
        UUID mailboxId = jdbc.queryForObject("select id from app_lm_mailbox_connection where workspace_id = ?::uuid",
                UUID.class, workspaceId);
        Instant lastRead = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofHours(1));
        jdbc.update("update app_lm_mailbox_connection set calendar_synced_at = ? where id = ?",
                Timestamp.from(lastRead), mailboxId);
        Instant claimedAt = lastRead.plus(Duration.ofMinutes(30)).plusNanos(123_000);
        Instant staleBefore = claimedAt.minus(Duration.ofMinutes(5));

        assertThat(mailboxes.claimCalendarRefresh(mailboxId, grantId, claimedAt, staleBefore)).isOne();
        assertThat(mailboxes.claimCalendarRefresh(mailboxId, grantId, claimedAt, staleBefore)).isZero();
        assertThat(mailboxes.claimCalendarRefresh(mailboxId, "another-grant", claimedAt.plus(Duration.ofHours(1)),
                claimedAt.plus(Duration.ofHours(1)))).isZero();

        assertThat(mailboxes.releaseCalendarRefresh(mailboxId, claimedAt, lastRead)).isOne();
        assertThat(syncedAt(mailboxId)).isEqualTo(lastRead);

        Instant laterClaim = claimedAt.plus(Duration.ofMinutes(10));
        assertThat(mailboxes.claimCalendarRefresh(mailboxId, grantId, laterClaim, laterClaim)).isOne();
        assertThat(mailboxes.releaseCalendarRefresh(mailboxId, claimedAt, lastRead)).isZero();
        assertThat(syncedAt(mailboxId)).isEqualTo(laterClaim);
    }

    @Test
    @DisplayName("booking a call invites the executive, moves them to Engaged and ends their run")
    void bookingACallEndsTheRun() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        startSequence(priya, "priya@" + domain);

        Instant slot = firstFreeSlot(priya, 30);
        book(priya, slot, 30, "priya@" + domain).andExpect(status().isNoContent());

        assertThat(gateway.created()).hasSize(1);
        assertThat(gateway.created().getFirst().inviteeAddress()).isEqualTo("priya@" + domain);
        assertThat(gateway.created().getFirst().endsAt()).isEqualTo(slot.plus(Duration.ofMinutes(30)));
        assertThat(candidateStatus(priya)).isEqualTo("engaged");
        assertThat(jdbc.queryForObject("select status from app_lm_outreach_enrollment where candidate_id = ?::uuid",
                String.class, priya)).isEqualTo("BOOKED");
        assertThat(activityKinds(priya)).contains("MEETING_BOOKED", "STATUS_CHANGED", "OUTREACH_STOPPED");

        JsonNode upcoming = meetingsOf(priya).get("upcoming");
        assertThat(upcoming).hasSize(1);
        assertThat(upcoming.get(0).get("bookedInUncava").asBoolean()).isTrue();

        // The calendar's own webhook for the same event keeps who booked it.
        changed(event(createdEventId(), "Confidential: first conversation",
                slot, MAILBOX, "priya@" + domain));
        assertThat(meetingsOf(priya).get("upcoming").get(0).get("bookedInUncava").asBoolean()).isTrue();

        JsonNode drawer = body(as(consultant, get(outreach("/candidates/" + priya))).andReturn());
        assertThat(drawer.get("run").get("status").asText()).isEqualTo("BOOKED");
        assertThat(drawer.get("steps").get(0).get("notSentBecause").asText()).isEqualTo("BOOKED");

        dispatcher.dispatchAt(nextMondayAtTen());
        assertThat(gateway.sent()).noneMatch(sent -> sent.email().to().equalsIgnoreCase("priya@" + domain));
    }

    @Test
    @DisplayName("a booked call moves status forward only: someone already Interested stays Interested")
    void bookingOnlyMovesStatusForward() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        setStatus(priya, "interested");
        long statusLines = activityKinds(priya).stream().filter("STATUS_CHANGED"::equals).count();

        book(priya, firstFreeSlot(priya, 45), 45, "priya@" + domain).andExpect(status().isNoContent());

        assertThat(candidateStatus(priya)).isEqualTo("interested");
        assertThat(activityKinds(priya)).contains("MEETING_BOOKED");
        assertThat(activityKinds(priya).stream().filter("STATUS_CHANGED"::equals).count()).isEqualTo(statusLines);
    }

    @Test
    @DisplayName("nothing is booked for someone marked do not contact, at an address not on file, or in a taken slot")
    void bookingRefusesWhatItShouldNot() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        connectMailbox();
        Instant slot = firstFreeSlot(priya, 30);

        assertThat(codeOf(book(priya, slot, 30, "someone@else.example").andExpect(status().isBadRequest())
                .andReturn())).isEqualTo("OUTREACH_ADDRESS_NOT_ON_FILE");
        assertThat(codeOf(book(priya, slot, 20, "priya@" + domain).andExpect(status().isBadRequest())
                .andReturn())).isEqualTo("MEETING_SLOT_INVALID");
        assertThat(codeOf(book(priya, slot.plus(Duration.ofMinutes(10)), 30, "priya@" + domain)
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("MEETING_SLOT_INVALID");

        gateway.busyAt(List.of(new BusyInterval(slot.plus(Duration.ofMinutes(15)), slot.plus(Duration.ofHours(1)))));
        assertThat(codeOf(book(priya, slot, 30, "priya@" + domain).andExpect(status().isConflict())
                .andReturn())).isEqualTo("MEETING_SLOT_TAKEN");
        gateway.busyAt(List.of());

        markDoNotContact(rajesh);
        assertThat(codeOf(book(rajesh, slot, 30, "rajesh@" + domain).andExpect(status().isConflict())
                .andReturn())).isEqualTo("MEETING_DO_NOT_CONTACT");

        assertThat(gateway.created()).isEmpty();
        assertThat(candidateStatus(priya)).isEqualTo("identified");
    }

    @Test
    @DisplayName("the slots leave out what the calendar holds, and nobody without a mailbox can book")
    void theSlotsReadTheCalendar() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        as(consultant, get(meetings(priya) + "/slots")).andExpect(status().isConflict());

        connectMailbox();
        JsonNode slots = body(as(consultant, get(meetings(priya) + "/slots").param("minutes", "30")).andReturn());
        assertThat(slots.get("address").asText()).isEqualTo(MAILBOX);
        assertThat(slots.get("timeZone").asText()).isEqualTo("Asia/Dubai");
        assertThat(slots.get("days")).hasSize(5);
        Instant taken = firstFreeSlot(priya, 30);

        gateway.busyAt(List.of(new BusyInterval(taken, taken.plus(Duration.ofMinutes(30)))));
        assertThat(firstFreeSlot(priya, 30)).isNotEqualTo(taken);
    }

    @Test
    @DisplayName("the grid pages ahead to a later week, up to its horizon, and a time there can be booked")
    void theSlotsPageAhead() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();

        JsonNode thisWeek = body(as(consultant, get(meetings(priya) + "/slots")).andExpect(status().isOk())
                .andReturn());
        LocalDate today = LocalDate.parse(thisWeek.get("earliestDate").asText());
        LocalDate latest = LocalDate.parse(thisWeek.get("latestDate").asText());
        assertThat(latest).isAfter(today.plusMonths(5));
        assertThat(thisWeek.get("previousFrom").isNull()).isTrue();

        LocalDate asked = today.plusWeeks(8);
        JsonNode later = body(as(consultant, get(meetings(priya) + "/slots").param("from", asked.toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(later.get("days")).hasSize(5);
        assertThat(LocalDate.parse(later.get("days").get(0).get("date").asText())).isBetween(asked, asked.plusDays(7));
        assertThat(LocalDate.parse(later.get("previousFrom").asText())).isBetween(asked.minusDays(9), asked);
        Instant farOff = Instant.parse(later.get("days").get(0).get("starts").get(0).asText());
        book(priya, farOff, 30, "priya@" + domain).andExpect(status().isNoContent());

        assertThat(codeOf(as(consultant, get(meetings(priya) + "/slots").param("from", latest.plusDays(1).toString()))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("MEETING_SLOT_INVALID");
    }

    @Test
    @DisplayName("a calendar that cannot be read is said so, never offered as free, and nothing is booked")
    void anUnreadableCalendarIsNotFree() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = firstFreeSlot(priya, 30);

        gateway.failBusyWith(unavailable("free-busy"));
        assertThat(codeOf(as(consultant, get(meetings(priya) + "/slots")).andExpect(status().isBadGateway())
                .andReturn())).isEqualTo("MEETING_CALENDAR_UNAVAILABLE");
        assertThat(codeOf(book(priya, slot, 30, "priya@" + domain).andExpect(status().isBadGateway())
                .andReturn())).isEqualTo("MEETING_CALENDAR_UNAVAILABLE");
        assertThat(gateway.created()).isEmpty();
    }

    @Test
    @DisplayName("a calendar whose read fails is tried again later, not on every poll, and is read once it can be")
    void aFailedCalendarReadBacksOff() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        gateway.calendarHolds(List.of(event("next", "Second conversation",
                Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(3)), MAILBOX, "priya@" + domain)));
        gateway.failCalendarWith(unavailable("calendar-events"));

        connectMailbox();
        assertThat(gateway.calendarReads()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select calendar_sync_attempts from app_lm_mailbox_connection "
                + "where workspace_id = ?::uuid", Integer.class, workspaceId)).isEqualTo(1);

        backfill.syncOwed();
        assertThat(gateway.calendarReads()).isEqualTo(1);

        gateway.failCalendarWith(null);
        jdbc.update("update app_lm_mailbox_connection set calendar_sync_retry_at = now() - interval '1 minute' "
                + "where workspace_id = ?::uuid", workspaceId);
        backfill.syncOwed();
        assertThat(gateway.calendarReads()).isEqualTo(2);
        assertThat(meetingsOf(priya).get("upcoming")).hasSize(1);
    }

    @Test
    @DisplayName("a client seat sees no meetings and cannot book; another workspace finds nothing")
    void clientSeatsAndOtherWorkspaces() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = firstFreeSlot(priya, 30);

        String rep = clientSeat();
        for (MockHttpServletRequestBuilder route : List.of(get(meetings(priya)), get(meetings(priya) + "/slots"),
                bookRequest(priya, slot, 30, "priya@" + domain))) {
            as(rep, route).andExpect(status().isForbidden());
        }

        String outsiderEmail = "omar@other-" + domain;
        createWorkspace(verifiedUser("Omar Farouk", outsiderEmail), "Other Search");
        String outsider = login(outsiderEmail);
        as(outsider, get(meetings(priya))).andExpect(status().isNotFound());
        assertThat(gateway.created()).isEmpty();
    }

    private static VendorException unavailable(String operation) {
        return new VendorException(VendorCall.of("nylas", operation), VendorFailureKind.UNAVAILABLE, null);
    }

    private void changed(CalendarEvent event) throws Exception {
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, event)));
        webhook();
    }

    private static CalendarEvent event(String id, String title, Instant start, String... participants) {
        return new CalendarEvent(id, title, start, start.plus(Duration.ofMinutes(30)), List.of(participants),
                "https://meet.example/" + id, "Google Meet");
    }

    private String createdEventId() {
        return jdbc.queryForObject("select provider_event_id from app_lm_person_meeting where workspace_id = ?::uuid "
                + "and booked_by_user_id is not null", String.class, workspaceId);
    }

    private int meetingRows() {
        return jdbc.queryForObject("select count(*) from app_lm_person_meeting where workspace_id = ?::uuid",
                Integer.class, workspaceId);
    }

    private JsonNode meetingsOf(String candidateId) throws Exception {
        return body(as(consultant, get(meetings(candidateId))).andExpect(status().isOk()).andReturn());
    }

    private Instant firstFreeSlot(String candidateId, int minutes) throws Exception {
        JsonNode slots = body(as(consultant, get(meetings(candidateId) + "/slots")
                .param("minutes", String.valueOf(minutes))).andExpect(status().isOk()).andReturn());
        for (JsonNode day : slots.get("days")) {
            if (!day.get("starts").isEmpty()) {
                return Instant.parse(day.get("starts").get(0).asText());
            }
        }
        throw new AssertionError("No free slot offered");
    }

    private ResultActions book(String candidateId, Instant start, int minutes, String address) throws Exception {
        return as(consultant, bookRequest(candidateId, start, minutes, address));
    }

    private MockHttpServletRequestBuilder bookRequest(String candidateId, Instant start, int minutes, String address) {
        return post(meetings(candidateId)).contentType(MediaType.APPLICATION_JSON).content("""
                {"startsAt":"%s","minutes":%d,"video":"GOOGLE_MEET","inviteAddress":"%s",
                 "title":"Confidential: first conversation"}
                """.formatted(start, minutes, address));
    }

    private void startSequence(String candidateId, String address) throws Exception {
        String sequenceId = body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"First approach","steps":[
                          {"delayWorkingDays":0,"subject":"Confidential: {{positionTitle}}","body":"Hi {{firstName}}."},
                          {"delayWorkingDays":3,"body":"Following up, {{firstName}}."}]}
                        """))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"people":[{"candidateId":"%s","toAddress":"%s","opener":null,"openerEdited":false}]}
                        """.formatted(candidateId, address)))
                .andExpect(status().isCreated());
    }

    private static Instant nextMondayAtTen() {
        return LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(10, 0)).atZone(DUBAI).toInstant();
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

    private void setStatus(String candidateId, String status) throws Exception {
        as(consultant, patch("/api/v1/projects/" + projectId + "/candidates/" + candidateId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk());
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

    private void markDoNotContact(String candidateId) throws Exception {
        String personId = body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId))
                .andReturn()).get("personId").asText();
        as(consultant, put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().isOk());
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

    private Instant syncedAt(UUID mailboxId) {
        return jdbc.queryForObject("select calendar_synced_at from app_lm_mailbox_connection where id = ?",
                Instant.class, mailboxId);
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

    private String meetings(String candidateId) {
        return outreach("/candidates/" + candidateId + "/meetings");
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }
}
