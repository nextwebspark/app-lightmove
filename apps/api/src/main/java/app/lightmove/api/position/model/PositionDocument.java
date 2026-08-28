package app.lightmove.api.position.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The position description attached to a brief — one file per position, stored in the row rather than
 * in object storage, because this is a single small document per mandate and not a library.
 *
 * <p>Nothing reads inside it. The bytes are kept so the file the mandate was briefed from stays with
 * the mandate; extracting fields out of it is a separate feature that does not exist.
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

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    public static PositionDocument of(UUID positionId, String fileName, String contentType,
                                      byte[] content, UUID uploadedBy) {
        PositionDocument document = new PositionDocument();
        document.positionId = positionId;
        document.uploadedBy = uploadedBy;
        document.replaceWith(fileName, contentType, content);
        return document;
    }

    /** Replacing keeps the row: one document per position, and the position owns the slot. */
    public void replaceWith(String fileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.fileSize = content.length;
    }
}
