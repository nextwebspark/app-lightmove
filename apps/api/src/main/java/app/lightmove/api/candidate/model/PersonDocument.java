package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A document on a workspace person (V105) — the card a researcher sees, whose files are its
 * {@link PersonDocumentVersion}s. Staff-only, read from every mandate that maps the person.
 */
@Entity
@Table(name = "app_lm_person_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonDocument extends BaseEntity {

    public static final int MAX_TITLE = 255;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private PersonDocumentCategory category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "name_key", nullable = false)
    private String nameKey;

    @Column(name = "primary_cv", nullable = false)
    private boolean primaryCv;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "project_title", updatable = false)
    private String projectTitle;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public static PersonDocument filed(Person person, PersonDocumentCategory category, String fileName,
                                       UUID projectId, String projectTitle, UUID uploader) {
        PersonDocument document = new PersonDocument();
        document.workspaceId = person.getWorkspaceId();
        document.personId = person.getId();
        document.category = category;
        document.title = titleOf(fileName);
        document.nameKey = nameKeyOf(fileName);
        document.projectId = projectId;
        document.projectTitle = projectTitle;
        document.createdBy = uploader;
        return document;
    }

    /** The lower-cased name an upload is matched on: the same file sent again is its next version. */
    public static String nameKeyOf(String fileName) {
        return fileName.strip().toLowerCase(Locale.ROOT);
    }

    /** The latest version answers to its own name from now on, so a file sent again under it lands here. */
    public void tookVersionNamed(String fileName) {
        this.nameKey = nameKeyOf(fileName);
    }

    public void rename(String newTitle) {
        this.title = newTitle;
    }

    /** A document that is no longer a CV cannot stay the person's CV. */
    public void recategorise(PersonDocumentCategory newCategory) {
        this.category = newCategory;
        if (newCategory != PersonDocumentCategory.CV) {
            this.primaryCv = false;
        }
    }

    public void markPrimaryCv(boolean primary) {
        this.primaryCv = primary && category == PersonDocumentCategory.CV;
    }

    public boolean isFiledBy(UUID userId) {
        return createdBy.equals(userId);
    }

    private static String titleOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String stem = (dot > 0 ? fileName.substring(0, dot) : fileName).strip();
        return stem.isEmpty() ? fileName : stem;
    }
}
