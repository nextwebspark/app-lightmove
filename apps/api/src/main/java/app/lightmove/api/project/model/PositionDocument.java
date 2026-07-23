package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.DocumentExtractionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The source document behind a position brief — 1:1 with {@link Position}, the same relationship
 * {@code Position} itself has to {@code Project}. Uploading replaces this row wholesale: there is no
 * history of prior uploads, only the current one, since {@link PositionService#uploadBriefDocument}
 * re-runs extraction from scratch on every upload.
 */
@Entity
@Table(name = "app_lm_position_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionDocument extends BaseEntity {

    @Column(name = "position_id", nullable = false, updatable = false)
    private UUID positionId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    // No @Lob: that maps a byte[] to Postgres's large-object OID type, not a plain bytea column — and
    // the migration declares `content bytea`, capped at lightmove.llm.max-document-size (15MB), nowhere
    // near large enough to need the OID large-object machinery.
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 16)
    private DocumentExtractionStatus extractionStatus;

    @Column(name = "extraction_error")
    private String extractionError;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    public static PositionDocument forPosition(UUID positionId) {
        PositionDocument document = new PositionDocument();
        document.positionId = positionId;
        return document;
    }

    /** Overwrites every field a re-upload can change. Identity ({@code positionId}) never moves. */
    public void replaceWith(String fileName, String contentType, long fileSize, byte[] content,
                            UUID uploadedBy) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.content = content;
        this.extractionStatus = DocumentExtractionStatus.COMPLETED;
        this.extractionError = null;
        this.uploadedBy = uploadedBy;
    }
}
