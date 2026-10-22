package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.util.UUID;

/**
 * The Notetaker on one of an executive's meetings, keyed on the meeting id the drawer already holds.
 * {@code joinable}: a call our bot can join; {@code enabled}: nobody turned it off for this call;
 * {@code status} is null until a bot was sent (or the call skipped).
 */
public record NotetakerMeetingResponse(UUID meetingId, NotetakerPlatform platform, boolean joinable, boolean enabled,
                                       UUID recordingId, NotetakerStatus status, NotetakerEndReason endReason,
                                       MeetingRecorderKind recorder) {}
