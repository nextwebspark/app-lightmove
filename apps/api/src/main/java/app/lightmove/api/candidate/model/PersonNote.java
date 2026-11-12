package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * A note on a workspace person (V96), read by staff only and as far as a {@link NoteViewpoint} reads. The position
 * it is about is kept by title too, so the note outlives it. A deleted note (V141) is invisible to every JPA read
 * until it is restored or purged; only {@link app.lightmove.api.candidate.repository.PersonNoteRepository}'s native
 * queries see it.
 */
@Entity
@Table(name = "app_lm_person_note")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonNote extends BaseEntity {

    public static final int MAX_BODY = 10000;
    public static final int MAX_TITLE = 200;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "project_title", updatable = false)
    private String projectTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private PersonNoteKind kind;

    @Column(name = "body", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", nullable = false, length = 16)
    private NoteFormat bodyFormat = NoteFormat.PLAIN;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "edited_by")
    private UUID editedBy;

    /** When a logged call, message or meeting took place; null for a plain note, where the writing is the event. */
    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "title", length = MAX_TITLE)
    private String title;

    @Column(name = "meeting_id")
    private UUID meetingId;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static PersonNote written(Person person, UUID projectId, String projectTitle, PersonNoteKind kind,
                                     String body, NoteFormat format, UUID author) {
        PersonNote note = new PersonNote();
        note.workspaceId = person.getWorkspaceId();
        note.personId = person.getId();
        note.projectId = projectId;
        note.projectTitle = projectTitle;
        note.kind = kind;
        note.body = body;
        note.bodyFormat = format;
        note.authorUserId = author;
        return note;
    }

    /** When it happened, its title and the meeting it is about; a plain note carries none of them. */
    public void placeAt(NoteOccurrence occurrence) {
        this.occurredAt = occurrence.occurredAt();
        this.title = occurrence.title();
        this.meetingId = occurrence.meetingId();
        this.recordingId = occurrence.recordingId();
    }

    /** When the event the note records happened: the logged time, or the writing for a note that logged none. */
    public Instant happenedAt() {
        return occurredAt != null ? occurredAt : getCreatedAt();
    }

    public void revise(PersonNoteKind newKind, String newBody, NoteFormat newFormat, UUID editor) {
        this.kind = newKind;
        this.body = newBody;
        this.bodyFormat = newFormat;
        this.editedAt = Instant.now();
        this.editedBy = editor;
    }

    public void discard(UUID by, Instant at) {
        this.deletedAt = at;
        this.deletedBy = by;
    }

    public void pin(boolean pinnedNow) {
        this.pinned = pinnedNow;
    }

    public boolean isWrittenBy(UUID userId) {
        return authorUserId.equals(userId);
    }

    public boolean isReadableFrom(NoteViewpoint viewpoint) {
        return viewpoint.reads(projectId);
    }
}
