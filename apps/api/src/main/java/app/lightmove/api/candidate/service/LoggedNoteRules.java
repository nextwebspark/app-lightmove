package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.model.LoggedMeeting;
import app.lightmove.api.candidate.model.NoteOccurrence;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What a note may say about when it happened and what it was about: a plain note is its own event and says neither,
 * nothing is logged in the future, a meeting's notes may name a past calendar meeting and take its time, and an
 * email is never logged by hand.
 */
@Component
@RequiredArgsConstructor
public class LoggedNoteRules {

    /** A clock a minute or two off the browser's is no reason to refuse "now". */
    private static final Duration FUTURE_GRACE = Duration.ofMinutes(5);

    private final PastMeetingLookup pastMeetings;
    private final Clock clock;

    /**
     * An email is the outreach ledger's, sent and recorded by the mailbox. A note filed as one before keeps its kind
     * through an edit; nothing else becomes one.
     */
    public void refuseLoggedEmail(PersonNoteKind kind, PersonNoteKind current) {
        if (kind == PersonNoteKind.EMAIL && current != PersonNoteKind.EMAIL) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "kind",
                    "An email is recorded when it is sent; log a call or a LinkedIn message instead");
        }
    }

    /** A new note's time, title and meeting, from what the request says. */
    public NoteOccurrence occurrenceOf(WritePersonNoteRequest request, PersonNoteKind kind, UUID workspaceId,
                                       UUID personId) {
        return revisedOccurrence(NoteOccurrence.NONE, request, kind, workspaceId, personId);
    }

    /**
     * An edited note's time, title and meeting: what the request leaves out is kept from {@code current} (an empty
     * title clears it), and whatever the note's kind cannot carry is let go — a plain note none of the three, any
     * kind but a meeting no meeting — so no edit, a change of kind included, breaks the table's checks.
     */
    public NoteOccurrence revisedOccurrence(NoteOccurrence current, WritePersonNoteRequest request,
                                            PersonNoteKind kind, UUID workspaceId, UUID personId) {
        String typedTitle = request.title() == null || request.title().isBlank() ? null : request.title().strip();
        if (kind == PersonNoteKind.GENERAL) {
            if (request.occurredAt() != null || typedTitle != null || request.meetingId() != null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "kind",
                        "A plain note has no time, title or meeting of its own");
            }
            return NoteOccurrence.NONE;
        }
        String title = request.title() == null ? current.title() : typedTitle;
        Instant now = clock.instant();
        if (request.meetingId() != null) {
            if (kind != PersonNoteKind.MEETING) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "meetingId",
                        "Only a meeting's notes name a meeting");
            }
            LoggedMeeting meeting = pastMeetings.pastMeetingOf(workspaceId, personId, request.meetingId(), now)
                    .orElseThrow(() -> ApiException.withField(ErrorCode.VALIDATION_FAILED, "meetingId",
                            "Choose a meeting with this person that has already happened"));
            return new NoteOccurrence(meeting.startsAt(), typedTitle != null ? typedTitle : meeting.title(),
                    meeting.meetingId());
        }
        if (request.occurredAt() != null) {
            if (request.occurredAt().isAfter(now.plus(FUTURE_GRACE))) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "occurredAt",
                        "Log what has already happened, not what is still to come");
            }
            // A time of its own says the meeting was not the calendar's: it is held outside Uncava from now on.
            return new NoteOccurrence(request.occurredAt(), title, null);
        }
        UUID meetingId = kind == PersonNoteKind.MEETING ? current.meetingId() : null;
        return new NoteOccurrence(current.occurredAt(), title, meetingId);
    }
}
