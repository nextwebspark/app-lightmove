package app.lightmove.api.outreach.model;

import java.time.Instant;
import java.util.UUID;

/** A kept meeting as another feature reads it: one calendar's event with one mapped person on it. */
public record KeptMeeting(UUID id, UUID workspaceId, UUID personId, UUID mailboxConnectionId, UUID userId,
                          String providerEventId, String title, Instant startsAt, Instant endsAt, String joinUrl,
                          String conferencingProvider) {

    public static KeptMeeting of(PersonMeeting row) {
        return new KeptMeeting(row.getId(), row.getWorkspaceId(), row.getPersonId(), row.getMailboxConnectionId(),
                row.getUserId(), row.getProviderEventId(), row.getTitle(), row.getStartsAt(), row.getEndsAt(),
                row.getJoinUrl(), row.getConferencingProvider());
    }
}
