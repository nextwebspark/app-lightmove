package app.lightmove.api.triagecompany.model;

import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.triagecompany.constant.CompanyNoteKind;
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
 * A note on one of a mandate's companies (V143), staff-only. A deleted note is invisible to every JPA read until it
 * is restored or purged; only the repository's native queries see it.
 */
@Entity
@Table(name = "app_lm_company_note")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyNote extends BaseEntity {

    public static final int MAX_BODY = 10000;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "triage_company_id", nullable = false, updatable = false)
    private UUID triageCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private CompanyNoteKind kind;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static CompanyNote written(UUID workspaceId, TriageCompany company, CompanyNoteKind kind, String body,
                                      NoteFormat format, UUID author) {
        CompanyNote note = new CompanyNote();
        note.workspaceId = workspaceId;
        note.projectId = company.getProjectId();
        note.triageCompanyId = company.getId();
        note.kind = kind;
        note.body = body;
        note.bodyFormat = format;
        note.authorUserId = author;
        return note;
    }

    public void revise(CompanyNoteKind newKind, String newBody, NoteFormat newFormat, UUID editor) {
        this.kind = newKind;
        this.body = newBody;
        this.bodyFormat = newFormat;
        this.editedAt = Instant.now();
        this.editedBy = editor;
    }

    public void pin(boolean pinnedNow) {
        this.pinned = pinnedNow;
    }

    public void discard(UUID by, Instant at) {
        this.deletedAt = at;
        this.deletedBy = by;
    }

    public boolean isWrittenBy(UUID userId) {
        return authorUserId.equals(userId);
    }
}
