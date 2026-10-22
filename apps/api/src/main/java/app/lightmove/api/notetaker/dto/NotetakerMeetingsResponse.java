package app.lightmove.api.notetaker.dto;

import java.util.List;

/** The Notetaker on an executive's meetings, and the two switches above the meeting's own. */
public record NotetakerMeetingsResponse(boolean offered, boolean workspaceEnabled, boolean myMeetingsEnabled,
                                        List<NotetakerMeetingResponse> meetings) {}
