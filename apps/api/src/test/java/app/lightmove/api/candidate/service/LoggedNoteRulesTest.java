package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.model.LoggedMeeting;
import app.lightmove.api.candidate.model.LoggedRecording;
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
    private static final UUID POSITION = UUID.randomUUID();
    private static final UUID MEETING = UUID.randomUUID();
    private static final UUID RECORDING = UUID.randomUUID();
    private static final Instant MEETING_START = NOW.minus(Duration.ofDays(3));

    private final LoggedNoteRules rules = new LoggedNoteRules(
            (workspaceId, personId, meetingId, now) -> meetingId.equals(MEETING)
                    ? Optional.of(new LoggedMeeting(MEETING, MEETING_START, "Coffee at DIFC"))
                    : Optional.empty(),
            (workspaceId, projectId, personId, recordingId, now) -> recordingId.equals(RECORDING)
                    && projectId.equals(POSITION)
                    ? Optional.of(new LoggedRecording(RECORDING, MEETING_START, "Screening call"))
                    : Optional.empty(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static WritePersonNoteRequest request(String kind, Instant occurredAt, String title, UUID meetingId) {
        return new WritePersonNoteRequest(kind, "Words.", null, null, null, occurredAt, title, meetingId, null);
    }

    private static WritePersonNoteRequest aboutRecording(String kind, UUID recordingId) {
        return new WritePersonNoteRequest(kind, "Words.", null, null, null, null, null, null, recordingId);
    }

    private NoteOccurrence revise(NoteOccurrence current, PersonNoteKind kind, WritePersonNoteRequest request) {
        return rules.revisedOccurrence(current, request, kind, WORKSPACE, POSITION, PERSON);
    }

    private NoteOccurrence write(WritePersonNoteRequest request, PersonNoteKind kind, UUID projectId) {
        return rules.occurrenceOf(request, kind, WORKSPACE, projectId, PERSON);
    }

    @Test
    void anEditOfTheWordsAloneKeepsWhenAndWhat() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING, null);

        assertThat(revise(current, PersonNoteKind.MEETING, request("meeting", null, null, null))).isEqualTo(current);
    }

    @Test
    void aTitleAloneKeepsTheMeetingAndItsTime() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING, null);

        assertThat(revise(current, PersonNoteKind.MEETING, request("meeting", null, "Debrief", null)))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Debrief", MEETING, null));
    }

    @Test
    void anEmptyTitleClearsIt() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "First call", null, null);

        assertThat(revise(current, PersonNoteKind.CALL, request("call", null, "", null)).title()).isNull();
    }

    @Test
    void becomingAPlainNoteLetsAllThreeGo() {
        NoteOccurrence current = new NoteOccurrence(MEETING_START, "First call", null, null);

        assertThat(revise(current, PersonNoteKind.GENERAL, request("general", null, null, null)))
                .isEqualTo(NoteOccurrence.NONE);
    }

    @Test
    void aMeetingNoteBecomingACallLetsTheMeetingAndRecordingGoAndKeepsItsTime() {
        NoteOccurrence onCalendar = new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING, null);
        NoteOccurrence recorded = new NoteOccurrence(MEETING_START, "Screening call", null, RECORDING);

        assertThat(revise(onCalendar, PersonNoteKind.CALL, request("call", null, null, null)))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Coffee at DIFC", null, null));
        assertThat(revise(recorded, PersonNoteKind.CALL, request("call", null, null, null)))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Screening call", null, null));
    }

    @Test
    void aMeetingNamedTakesItsStartAndTitle() {
        assertThat(write(request("meeting", null, null, MEETING), PersonNoteKind.MEETING, POSITION))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Coffee at DIFC", MEETING, null));
    }

    @Test
    void aRecordingNamedTakesItsStartAndTitleOnlyFromItsOwnPosition() {
        assertThat(write(aboutRecording("meeting", RECORDING), PersonNoteKind.MEETING, POSITION))
                .isEqualTo(new NoteOccurrence(MEETING_START, "Screening call", null, RECORDING));
        assertThatThrownBy(() -> write(aboutRecording("meeting", RECORDING), PersonNoteKind.MEETING, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> write(aboutRecording("meeting", RECORDING), PersonNoteKind.MEETING,
                UUID.randomUUID())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> write(new WritePersonNoteRequest("meeting", "Words.", null, null, null, null, null,
                MEETING, RECORDING), PersonNoteKind.MEETING, POSITION)).isInstanceOf(ApiException.class);
    }

    @Test
    void refusesTheFutureAPlainNotesTimeAndAMeetingOrRecordingOnACall() {
        assertThatThrownBy(() -> write(request("call", NOW.plus(Duration.ofHours(1)), null, null),
                PersonNoteKind.CALL, POSITION)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> write(request("general", NOW, null, null),
                PersonNoteKind.GENERAL, POSITION)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> write(request("call", null, null, MEETING),
                PersonNoteKind.CALL, POSITION)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> write(aboutRecording("call", RECORDING),
                PersonNoteKind.CALL, POSITION)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> write(request("meeting", null, null, UUID.randomUUID()),
                PersonNoteKind.MEETING, POSITION)).isInstanceOf(ApiException.class);
    }

    @Test
    void anEmailIsNeverLoggedButAnOldOneKeepsItsKind() {
        assertThatThrownBy(() -> rules.refuseLoggedEmail(PersonNoteKind.EMAIL, null)).isInstanceOf(ApiException.class);
        rules.refuseLoggedEmail(PersonNoteKind.EMAIL, PersonNoteKind.EMAIL);
    }
}
