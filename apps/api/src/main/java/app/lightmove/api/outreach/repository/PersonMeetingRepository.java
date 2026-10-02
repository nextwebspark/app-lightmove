package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.PersonMeeting;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Meetings with people. Every read carries the workspace; the writes are keyed on the mailbox they came from. */
public interface PersonMeetingRepository extends JpaRepository<PersonMeeting, UUID> {

    List<PersonMeeting> findByWorkspaceIdAndPersonIdOrderByStartsAtAsc(UUID workspaceId, UUID personId);

    /**
     * A booking that arrives again on the calendar's own webhook keeps who booked it, and a join link the
     * calendar has not filled in yet does not erase the one already held.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO app_lm_person_meeting (workspace_id, person_id, mailbox_connection_id, user_id,
                                               provider_event_id, title, starts_at, ends_at, join_url,
                                               conferencing_provider, booked_by_user_id, booked_via_link)
            VALUES (:workspaceId, :personId, :mailboxConnectionId, :userId, :providerEventId, :title, :startsAt,
                    :endsAt, :joinUrl, :conferencingProvider, :bookedByUserId, :bookedViaLink)
            ON CONFLICT (mailbox_connection_id, provider_event_id, person_id) DO UPDATE SET
                title                 = EXCLUDED.title,
                starts_at             = EXCLUDED.starts_at,
                ends_at               = EXCLUDED.ends_at,
                join_url              = COALESCE(EXCLUDED.join_url, app_lm_person_meeting.join_url),
                conferencing_provider = COALESCE(EXCLUDED.conferencing_provider,
                                                 app_lm_person_meeting.conferencing_provider),
                booked_by_user_id     = COALESCE(app_lm_person_meeting.booked_by_user_id, EXCLUDED.booked_by_user_id),
                booked_via_link       = app_lm_person_meeting.booked_via_link OR EXCLUDED.booked_via_link
            """)
    void upsert(UUID workspaceId, UUID personId, UUID mailboxConnectionId, UUID userId, String providerEventId,
                String title, Instant startsAt, Instant endsAt, String joinUrl, String conferencingProvider,
                UUID bookedByUserId, boolean bookedViaLink);

    /** An event that no longer has these people on it. */
    @Modifying
    @Query("delete from PersonMeeting m where m.mailboxConnectionId = :mailboxConnectionId "
            + "and m.providerEventId = :providerEventId and m.personId not in :keptPersonIds")
    int deleteDroppedFromEvent(UUID mailboxConnectionId, String providerEventId, Collection<UUID> keptPersonIds);

    @Modifying
    @Query("delete from PersonMeeting m where m.mailboxConnectionId = :mailboxConnectionId "
            + "and m.providerEventId = :providerEventId")
    int deleteEvent(UUID mailboxConnectionId, String providerEventId);
}
