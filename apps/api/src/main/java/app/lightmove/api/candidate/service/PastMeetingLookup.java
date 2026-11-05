package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.LoggedMeeting;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's calendar meeting that is already over, for notes logged about it. Answered by {@code outreach}, which
 * keeps the meetings and already depends on {@code candidate}, so the dependency stays one-way.
 */
public interface PastMeetingLookup {

    Optional<LoggedMeeting> pastMeetingOf(UUID workspaceId, UUID personId, UUID meetingId, Instant now);
}
