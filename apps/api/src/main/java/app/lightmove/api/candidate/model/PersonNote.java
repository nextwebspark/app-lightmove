package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonNoteKind;
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

/**
 * A note on a workspace person (V96), readable from every mandate that maps them and by staff only.
 * The mandate it was written about is context, kept by title too so the note outlives the mandate.
 */
@Entity
@Table(name = "app_lm_person_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonNote extends BaseEntity {

    public static final int MAX_BODY = 4000;

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

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "edited_by")
    private UUID editedBy;

    public static PersonNote written(Person person, UUID projectId, String projectTitle, PersonNoteKind kind,
                                     String body, UUID author) {
        PersonNote note = new PersonNote();
        note.workspaceId = person.getWorkspaceId();
        note.personId = person.getId();
        note.projectId = projectId;
        note.projectTitle = projectTitle;
        note.kind = kind;
        note.body = body;
        note.authorUserId = author;
        return note;
    }

    public void revise(PersonNoteKind newKind, String newBody, UUID editor) {
        this.kind = newKind;
        this.body = newBody;
        this.editedAt = Instant.now();
        this.editedBy = editor;
    }

    public void pin(boolean pinnedNow) {
        this.pinned = pinnedNow;
    }

    public boolean isWrittenBy(UUID userId) {
        return authorUserId.equals(userId);
    }
}
