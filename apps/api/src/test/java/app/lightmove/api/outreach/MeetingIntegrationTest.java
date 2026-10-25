package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import app.lightmove.api.outreach.service.MeetingBackfill;
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
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Calls");
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
    @DisplayName("booking a call invites the executive, moves them to Engaged and ends their run")
    void bookingACallEndsTheRun() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        startSequence(priya, "priya@" + domain);

        Instant slot = tomorrowAt(10, 0);
        String bookedId = body(book(priya, slot, 30, "priya@" + domain).andExpect(status().isOk()).andReturn())
                .get("meetingId").asText();

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
        assertThat(upcoming.get(0).get("id").asText()).isEqualTo(bookedId);

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

        book(priya, tomorrowAt(10, 0), 45, "priya@" + domain).andExpect(status().isOk());

        assertThat(candidateStatus(priya)).isEqualTo("interested");
        assertThat(activityKinds(priya)).contains("MEETING_BOOKED");
        assertThat(activityKinds(priya).stream().filter("STATUS_CHANGED"::equals).count()).isEqualTo(statusLines);
    }

    @Test
    @DisplayName("a call runs any length on five-minute marks, up to a working day, and its time shows as taken")
    void aCallRunsAnyLength() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = tomorrowAt(10, 15);

        book(priya, slot, 50, "priya@" + domain).andExpect(status().isOk());

        Instant callEnds = slot.plus(Duration.ofMinutes(50));
        assertThat(gateway.created().getFirst().endsAt()).isEqualTo(callEnds);
        gateway.busyAt(List.of(new BusyInterval(slot, callEnds)));
        JsonNode view = calendarOf(priya, slot.atZone(DUBAI).toLocalDate());
        assertThat(view.get("busy")).hasSize(1);
        assertThat(Instant.parse(view.get("busy").get(0).get("start").asText())).isEqualTo(slot);
        assertThat(Instant.parse(view.get("busy").get(0).get("end").asText())).isEqualTo(callEnds);
        assertThat(view.get("minMinutes").asInt()).isEqualTo(5);
        assertThat(view.get("maxMinutes").asInt()).isEqualTo(480);
    }

    @Test
    @DisplayName("nothing is booked for someone marked do not contact, at an address not on file, or in a taken slot")
    void bookingRefusesWhatItShouldNot() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        connectMailbox();
        Instant slot = tomorrowAt(10, 0);

        assertThat(codeOf(book(priya, slot, 30, "someone@else.example").andExpect(status().isBadRequest())
                .andReturn())).isEqualTo("OUTREACH_ADDRESS_NOT_ON_FILE");
        for (int minutes : new int[] {0, 12, 485}) {
            assertThat(codeOf(book(priya, slot, minutes, "priya@" + domain).andExpect(status().isBadRequest())
                    .andReturn())).isEqualTo("MEETING_SLOT_INVALID");
        }
        assertThat(codeOf(book(priya, slot.plus(Duration.ofMinutes(7)), 30, "priya@" + domain)
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("MEETING_SLOT_INVALID");
        Instant past = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(1));
        assertThat(codeOf(book(priya, past, 30, "priya@" + domain).andExpect(status().isBadRequest())
                .andReturn())).isEqualTo("MEETING_SLOT_INVALID");

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
    @DisplayName("the calendar view shows a week of busy times and working hours, and nobody without a mailbox books")
    void theCalendarViewReadsTheCalendar() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        as(consultant, get(meetings(priya) + "/calendar")).andExpect(status().isConflict());

        connectMailbox();
        Instant taken = tomorrowAt(11, 0);
        gateway.busyAt(List.of(new BusyInterval(taken, taken.plus(Duration.ofMinutes(45)))));
        JsonNode view = calendarOf(priya, null);

        assertThat(view.get("address").asText()).isEqualTo(MAILBOX);
        assertThat(view.get("timeZone").asText()).isEqualTo("Asia/Dubai");
        assertThat(view.get("days").asInt()).isEqualTo(7);
        assertThat(view.get("from").asText()).isEqualTo(view.get("earliestDate").asText());
        assertThat(view.get("busy")).hasSize(1);
        assertThat(view.get("workingHoursSource").asText()).isEqualTo("WORKSPACE");
        List<Instant> opens = new ArrayList<>();
        view.get("workingHours").forEach(span -> opens.add(Instant.parse(span.get("start").asText())));
        // A week always holds the five working days a workspace with no country keeps, Monday to Friday 08:00–18:00.
        assertThat(opens).isNotEmpty().allMatch(open -> {
            DayOfWeek day = open.atZone(DUBAI).getDayOfWeek();
            return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
        });
        assertThat(view.get("workingHours")).hasSize(5);
    }

    @Test
    @DisplayName("a consultant books outside their working hours, at a minute of their choosing, on any day")
    void aConsultantIsNotHeldToWorkingHours() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant saturdayEvening = LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                .atTime(19, 40).atZone(DUBAI).toInstant();

        book(priya, saturdayEvening, 25, "priya@" + domain).andExpect(status().isOk());

        assertThat(gateway.created().getFirst().startsAt()).isEqualTo(saturdayEvening);
    }

    @Test
    @DisplayName("the view pages ahead to a later week, up to its horizon, and a time there can be booked")
    void theCalendarViewPagesAhead() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();

        JsonNode thisWeek = calendarOf(priya, null);
        LocalDate today = LocalDate.parse(thisWeek.get("earliestDate").asText());
        LocalDate latest = LocalDate.parse(thisWeek.get("latestDate").asText());
        assertThat(latest).isAfter(today.plusMonths(5));

        LocalDate asked = today.plusWeeks(8);
        JsonNode later = calendarOf(priya, asked);
        assertThat(LocalDate.parse(later.get("from").asText())).isEqualTo(asked);
        assertThat(LocalDate.parse(calendarOf(priya, today.minusDays(3)).get("from").asText())).isEqualTo(today);
        book(priya, asked.atTime(9, 30).atZone(DUBAI).toInstant(), 30, "priya@" + domain)
                .andExpect(status().isOk());

        assertThat(codeOf(as(consultant, get(meetings(priya) + "/calendar").param("from", latest.plusDays(1).toString()))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("MEETING_SLOT_INVALID");
        assertThat(codeOf(as(consultant, get(meetings(priya) + "/calendar").param("days", "8"))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a calendar that cannot be read is said so, never offered as free, and nothing is booked")
    void anUnreadableCalendarIsNotFree() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = tomorrowAt(10, 0);

        gateway.failBusyWith(unavailable("free-busy"));
        assertThat(codeOf(as(consultant, get(meetings(priya) + "/calendar")).andExpect(status().isBadGateway())
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
        Instant slot = tomorrowAt(10, 0);

        String rep = clientSeat();
        for (MockHttpServletRequestBuilder route : List.of(get(meetings(priya)), get(meetings(priya) + "/calendar"),
                bookRequest(priya, slot, 30, "priya@" + domain))) {
            as(rep, route).andExpect(status().isForbidden());
        }

        String outsiderEmail = "omar@other-" + domain;
        createWorkspace(verifiedUser("Omar Farouk", outsiderEmail), "Other Search");
        String outsider = login(outsiderEmail);
        as(outsider, get(meetings(priya))).andExpect(status().isNotFound());
        assertThat(gateway.created()).isEmpty();
    }

    @Test
    @DisplayName("a call booked in Uncava is moved to another free time: the executive is told, and nothing else moves")
    void reschedulingMovesTheCall() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        startSequence(priya, "priya@" + domain);
        Instant slot = tomorrowAt(10, 0);
        book(priya, slot, 30, "priya@" + domain).andExpect(status().isOk());
        JsonNode booked = meetingsOf(priya).get("upcoming").get(0);
        assertThat(booked.get("editable").asBoolean()).isTrue();
        String meetingId = booked.get("id").asText();

        Instant later = slot.plus(Duration.ofHours(2));
        reschedule(priya, meetingId, later, 30).andExpect(status().isNoContent());

        assertThat(gateway.moves()).containsExactly(new RecordingMailboxGateway.EventMove(createdEventId(), later,
                later.plus(Duration.ofMinutes(30))));
        JsonNode moved = meetingsOf(priya).get("upcoming").get(0);
        assertThat(Instant.parse(moved.get("startsAt").asText())).isEqualTo(later);
        assertThat(moved.get("bookedInUncava").asBoolean()).isTrue();
        assertThat(activityKinds(priya)).endsWith("MEETING_RESCHEDULED");
        assertThat(candidateStatus(priya)).isEqualTo("engaged");
        assertThat(jdbc.queryForObject("select status from app_lm_outreach_enrollment where candidate_id = ?::uuid",
                String.class, priya)).isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where event_type = "
                + "'OUTREACH_MEETING_RESCHEDULED' and workspace_id = ?::uuid", Integer.class, workspaceId)).isOne();
    }

    @Test
    @DisplayName("a call's own time is free to move it within, and the calendar is still asked for the rest")
    void reschedulingTreatsItsOwnTimeAsFree() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = tomorrowAt(10, 0);
        book(priya, slot, 30, "priya@" + domain).andExpect(status().isOk());
        String meetingId = meetingsOf(priya).get("upcoming").get(0).get("id").asText();
        gateway.busyAt(List.of(new BusyInterval(slot, slot.plus(Duration.ofMinutes(30))),
                new BusyInterval(slot.plus(Duration.ofMinutes(30)), slot.plus(Duration.ofMinutes(45)))));
        assertThat(codeOf(reschedule(priya, meetingId, slot, 45).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("MEETING_SLOT_TAKEN");
        assertThat(gateway.moves()).isEmpty();

        gateway.busyAt(List.of(new BusyInterval(slot, slot.plus(Duration.ofMinutes(30)))));
        assertThat(as(consultant, get(meetings(priya) + "/calendar")
                .param("from", slot.atZone(DUBAI).toLocalDate().toString()).param("excluding", meetingId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("\"busy\":[]");
        reschedule(priya, meetingId, slot, 45).andExpect(status().isNoContent());
        assertThat(gateway.moves()).hasSize(1);
    }

    @Test
    @DisplayName("cancelling a call booked in Uncava tells the executive and takes it off the drawer; the status stays")
    void cancellingRemovesTheCall() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        book(priya, tomorrowAt(10, 0), 30, "priya@" + domain).andExpect(status().isOk());
        String eventId = createdEventId();
        String meetingId = meetingsOf(priya).get("upcoming").get(0).get("id").asText();

        as(consultant, delete(meetings(priya) + "/" + meetingId)).andExpect(status().isNoContent());

        assertThat(gateway.cancelled()).containsExactly(eventId);
        assertThat(meetingsOf(priya).get("upcoming")).isEmpty();
        assertThat(meetingRows()).isZero();
        assertThat(activityKinds(priya)).endsWith("MEETING_CANCELLED");
        assertThat(candidateStatus(priya)).isEqualTo("engaged");
    }

    @Test
    @DisplayName("only an upcoming call booked in Uncava, by whoever holds its calendar, is moved or cancelled here")
    void onlyTheHolderChangesAnUncavaCall() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(2));
        changed(event("found", "Lunch", start, MAILBOX, "priya@" + domain));
        JsonNode found = meetingsOf(priya).get("upcoming").get(0);
        assertThat(found.get("editable").asBoolean()).isFalse();
        assertThat(codeOf(as(consultant, delete(meetings(priya) + "/" + found.get("id").asText()))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("MEETING_NOT_EDITABLE");

        Instant slot = tomorrowAt(10, 0);
        book(priya, slot, 30, "priya@" + domain).andExpect(status().isOk());
        String booked = meetingIdBookedInUncava();
        inviteAndAccept(consultant, "Omar Saleh", "omar@" + domain, "ADMIN");
        String teammate = login("omar@" + domain);
        assertThat(codeOf(mvc.perform(delete(meetings(priya) + "/" + booked)
                        .header("Authorization", "Bearer " + teammate))
                .andExpect(status().isForbidden()).andReturn())).isEqualTo("MEETING_NOT_YOURS");

        markDoNotContact(priya);
        assertThat(codeOf(reschedule(priya, booked, slot.plus(Duration.ofHours(2)), 30)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("MEETING_DO_NOT_CONTACT");
        assertThat(gateway.moves()).isEmpty();
        assertThat(gateway.cancelled()).isEmpty();
    }

    @Test
    @DisplayName("a calendar that refuses the change leaves the call as it was, and says to check the calendar")
    void aRefusedChangeLeavesTheCall() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = tomorrowAt(10, 0);
        book(priya, slot, 30, "priya@" + domain).andExpect(status().isOk());
        String meetingId = meetingsOf(priya).get("upcoming").get(0).get("id").asText();

        gateway.failChangingEvents(unavailable("move-event"));
        assertThat(codeOf(reschedule(priya, meetingId, slot.plus(Duration.ofHours(2)), 30)
                .andExpect(status().isBadGateway()).andReturn())).isEqualTo("MEETING_UPDATE_FAILED");
        assertThat(codeOf(as(consultant, delete(meetings(priya) + "/" + meetingId))
                .andExpect(status().isBadGateway()).andReturn())).isEqualTo("MEETING_UPDATE_FAILED");

        JsonNode still = meetingsOf(priya).get("upcoming").get(0);
        assertThat(Instant.parse(still.get("startsAt").asText())).isEqualTo(slot);
        assertThat(activityKinds(priya)).doesNotContain("MEETING_RESCHEDULED", "MEETING_CANCELLED");
    }

    @Test
    @DisplayName("a call the calendar no longer holds is said so and dropped, and the mailbox stays connected")
    void aCallGoneFromTheCalendarLeavesTheMailboxAlone() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        Instant slot = tomorrowAt(10, 0);
        book(priya, slot, 30, "priya@" + domain).andExpect(status().isOk());
        String meetingId = meetingsOf(priya).get("upcoming").get(0).get("id").asText();

        gateway.failChangingEvents(new VendorException(VendorCall.of("nylas", "move-event"),
                VendorFailureKind.NOT_FOUND, null));
        assertThat(codeOf(reschedule(priya, meetingId, slot.plus(Duration.ofHours(2)), 30)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("MEETING_GONE");

        assertThat(jdbc.queryForObject("select status from app_lm_mailbox_connection where workspace_id = ?::uuid",
                String.class, workspaceId)).isEqualTo("ACTIVE");
        assertThat(meetingsOf(priya).get("upcoming")).isEmpty();
        assertThat(activityKinds(priya)).doesNotContain("MEETING_RESCHEDULED");
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

    /** Tomorrow at a given time in the consultant's zone: never too soon to book, and the calendar starts empty. */
    private static Instant tomorrowAt(int hour, int minute) {
        return LocalDate.now(DUBAI).plusDays(1).atTime(hour, minute).atZone(DUBAI).toInstant();
    }

    private JsonNode calendarOf(String candidateId, LocalDate from) throws Exception {
        MockHttpServletRequestBuilder request = get(meetings(candidateId) + "/calendar");
        if (from != null) {
            request.param("from", from.toString());
        }
        return body(as(consultant, request).andExpect(status().isOk()).andReturn());
    }

    private String meetingIdBookedInUncava() {
        return jdbc.queryForObject("select id::text from app_lm_person_meeting where workspace_id = ?::uuid "
                + "and booked_by_user_id is not null", String.class, workspaceId);
    }

    private ResultActions reschedule(String candidateId, String meetingId, Instant start, int minutes)
            throws Exception {
        return as(consultant, put(meetings(candidateId) + "/" + meetingId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startsAt":"%s","minutes":%d}
                        """.formatted(start, minutes)));
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
