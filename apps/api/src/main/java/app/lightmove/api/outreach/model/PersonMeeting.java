package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A calendar event with a mapped person on it (V102), one row per matching person. Read through here,
 * written only by {@code PersonMeetingRepository.upsert}, so a webhook and a booking racing on one event
 * land on one row.
 */
@Entity
@Table(name = "app_lm_person_meeting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonMeeting extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "mailbox_connection_id", nullable = false, updatable = false)
    private UUID mailboxConnectionId;

    /** The consultant whose calendar holds the event. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider_event_id", nullable = false, updatable = false)
    private String providerEventId;

    @Column(name = "title")
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "join_url")
    private String joinUrl;

    @Column(name = "conferencing_provider", length = 64)
    private String conferencingProvider;

    @Column(name = "booked_by_user_id")
    private UUID bookedByUserId;

    @Column(name = "booked_via_link", nullable = false)
    private boolean bookedViaLink;
}
