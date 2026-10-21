package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.AttachmentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One file a consultant's own email carries, never changed once written; its bytes are under {@code storageKey}. */
@Entity
@Table(name = "app_lm_outreach_message_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachMessageAttachment {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private AttachmentSource source;

    @Column(name = "document_version_id", updatable = false)
    private UUID documentVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OutreachMessageAttachment(OutreachMessage message, int position, StagedAttachment file) {
        this.messageId = message.getId();
        this.workspaceId = message.getWorkspaceId();
        this.position = position;
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        this.sha256 = file.sha256();
        this.storageKey = file.storageKey();
        this.documentVersionId = file.documentVersionId();
        this.source = file.documentVersionId() == null ? AttachmentSource.UPLOAD : AttachmentSource.PERSON_DOCUMENT;
        this.createdAt = Instant.now();
    }

    public boolean isPersonDocument() {
        return source == AttachmentSource.PERSON_DOCUMENT;
    }
}
