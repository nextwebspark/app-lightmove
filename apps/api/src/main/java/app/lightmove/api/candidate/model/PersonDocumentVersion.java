package app.lightmove.api.candidate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One uploaded file of a {@link PersonDocument}, never changed once written; its bytes are under {@code storageKey}. */
@Entity
@Table(name = "app_lm_person_document_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonDocumentVersion {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    public PersonDocumentVersion(PersonDocument document, int versionNo, StagedDocumentFile file, UUID uploader) {
        this.documentId = document.getId();
        this.personId = document.getPersonId();
        this.versionNo = versionNo;
        this.fileName = file.fileName();
        this.contentType = file.format().contentType();
        this.sizeBytes = file.sizeBytes();
        this.sha256 = file.sha256();
        this.storageKey = file.storageKey();
        this.uploadedBy = uploader;
        this.uploadedAt = Instant.now();
    }

    public boolean isUploadedBy(UUID userId) {
        return uploadedBy.equals(userId);
    }
}
