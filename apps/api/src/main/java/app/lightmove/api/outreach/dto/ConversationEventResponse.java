package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.PersonTimelineEntryResponse;
import app.lightmove.api.outreach.constant.ConversationEventType;
import java.time.Instant;

/** One entry of an executive's communication history; exactly the payload {@code type} names is set. */
public record ConversationEventResponse(
        ConversationEventType type,
        Instant at,
        ConversationMessageResponse message,
        PersonTimelineEntryResponse activity,
        PersonNoteResponse note,
        MeetingResponse meeting
) {

    public static ConversationEventResponse of(ConversationMessageResponse message) {
        return new ConversationEventResponse(ConversationEventType.MESSAGE, message.at(), message, null, null, null);
    }

    public static ConversationEventResponse of(PersonTimelineEntryResponse activity) {
        return new ConversationEventResponse(ConversationEventType.ACTIVITY, activity.occurredAt(), null, activity,
                null, null);
    }

    public static ConversationEventResponse of(PersonNoteResponse note) {
        return new ConversationEventResponse(ConversationEventType.NOTE, note.createdAt(), null, null, note, null);
    }

    public static ConversationEventResponse of(MeetingResponse meeting) {
        return new ConversationEventResponse(ConversationEventType.MEETING, meeting.startsAt(), null, null, null,
                meeting);
    }
}
