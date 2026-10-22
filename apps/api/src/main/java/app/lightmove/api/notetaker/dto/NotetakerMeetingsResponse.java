package app.lightmove.api.notetaker.dto;

import java.util.List;

/**
 * The Notetaker on an executive's meetings, the two switches above the meeting's own, and every call whose note is
 * filed where this position may read it, whether or not its meeting is still listed.
 */
public record NotetakerMeetingsResponse(boolean offered, boolean workspaceEnabled, boolean myMeetingsEnabled,
                                        List<NotetakerMeetingResponse> meetings,
                                        List<FiledRecordingResponse> recordings) {}
