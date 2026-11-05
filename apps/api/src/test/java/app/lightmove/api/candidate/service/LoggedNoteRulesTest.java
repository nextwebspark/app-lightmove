package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.model.LoggedMeeting;
import app.lightmove.api.candidate.model.NoteOccurrence;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoggedNoteRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();
    private static final UUID MEETING = UUID.randomUUID();
    private static final Instant MEETING_START = NOW.minus(Duration.ofDays(3));

    private final LoggedNoteRules rules = new LoggedNoteRules(
            (workspaceId, personId, meetingId, now) -> meetingId.equals(MEETING)
                    ? Optional.of(new LoggedMeeting(MEETING, MEETING_START, "Coffee at DIFC"))
                    : Optional.empty(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static WritePersonNoteRequest request(String kind, Instant occurredAt, String title, UUID meetingId) {
        return new WritePersonNoteRequest(kind, "Words.", null, null, null, occurredAt, title, meetingId);
    }

    private NoteOccurrence revise(NoteOccurrence current, PersonNoteKind kind, WritePersonNoteRequest request) {
        return rules.revisedOccurrence(current, request, kind, WORKSPACE, PERSON);
    }

    @Test
    void anEditOfTheWordsAloneKeepsWhenAndWhat() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING);

        assertThat(revise(current, PersonNoteKind.MEETING, request("meeting", null, null, null))).isEqualTo(current);
    }

    @Test
    void aTitleAloneKeepsTheMeetingAndItsTime() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING);

        assertThat(revise(current, PersonNoteKind.MEETING, request("meeting", null, "Debrief", null)))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Debrief", MEETING));
    }

    @Test
    void anEmptyTitleClearsIt() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "First call", null);

        assertThat(revise(current, PersonNoteKind.CALL, request("call", null, "", null)).title()).isNull();
    }

    @Test
    void becomingAPlainNoteLetsAllThreeGo() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "First call", null);

        assertThat(revise(current, PersonNoteKind.GENERAL, request("general", null, null, null)))
                .isEqualTo(NoteOccurrence.NONE);
    }

    @Test
    void aMeetingNoteBecomingACallLetsTheMeetingGoAndKeepsItsTime() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING);

        assertThat(revise(current, PersonNoteKind.CALL, request("call", null, null, null)))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Coffee at DIFC", null));
    }

    @Test
    void aMeetingNamedTakesItsStartAndTitle() {
        assertThat(rules.occurrenceOf(request("meeting", null, null, MEETING), PersonNoteKind.MEETING, WORKSPACE,
                PERSON)).isEqualTo(new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING));
    }

    @Test
    void refusesTheFutureAPlainNotesTimeAndAMeetingOnACall() {
        assertThatThrownBy(() -> rules.occurrenceOf(request("call", NOW.plus(Duration.ofHours(1)), null, null),
                PersonNoteKind.CALL, WORKSPACE, PERSON)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> rules.occurrenceOf(request("general", NOW, null, null),
                PersonNoteKind.GENERAL, WORKSPACE, PERSON)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> rules.occurrenceOf(request("call", null, null, MEETING),
                PersonNoteKind.CALL, WORKSPACE, PERSON)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> rules.occurrenceOf(request("meeting", null, null, UUID.randomUUID()),
                PersonNoteKind.MEETING, WORKSPACE, PERSON)).isInstanceOf(ApiException.class);
    }

    @Test
    void anEmailIsNeverLoggedButAnOldOneKeepsItsKind() {
        assertThatThrownBy(() -> rules.refuseLoggedEmail(PersonNoteKind.EMAIL, null)).isInstanceOf(ApiException.class);
        rules.refuseLoggedEmail(PersonNoteKind.EMAIL, PersonNoteKind.EMAIL);
    }
}
