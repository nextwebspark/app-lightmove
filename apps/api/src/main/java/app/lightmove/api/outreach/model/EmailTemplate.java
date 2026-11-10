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
 * A saved email or reply a consultant applies in the composer. Personal while {@code sharedAt} is null; shared, every
 * staff member of the workspace applies it, and only its owner or a workspace admin changes it.
 */
@Entity
@Table(name = "app_lm_email_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailTemplate extends BaseEntity {

    public static final int MAX_NAME_LENGTH = 80;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", nullable = false, length = 10000)
    private String body;

    @Column(name = "shared_at")
    private Instant sharedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static EmailTemplate of(UUID workspaceId, UUID ownerUserId, EmailTemplateContent content, boolean shared) {
        EmailTemplate template = new EmailTemplate();
        template.workspaceId = workspaceId;
        template.ownerUserId = ownerUserId;
        template.write(content, ownerUserId);
        template.sharedAt = shared ? Instant.now() : null;
        return template;
    }

    /** A personal copy of {@code source}, owned by {@code ownerUserId}, named "Copy of …" within the name's limit. */
    public static EmailTemplate copyOf(EmailTemplate source, UUID ownerUserId) {
        String name = "Copy of " + source.name;
        return of(source.workspaceId, ownerUserId, new EmailTemplateContent(
                name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).strip() : name,
                source.subject, source.body), false);
    }

    public void write(EmailTemplateContent content, UUID editorId) {
        this.name = content.name();
        this.subject = content.subject();
        this.body = content.body();
        this.updatedBy = editorId;
    }

    public void share(boolean shared, UUID editorId) {
        this.sharedAt = shared ? (sharedAt == null ? Instant.now() : sharedAt) : null;
        this.updatedBy = editorId;
    }

    public boolean isShared() {
        return sharedAt != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId.equals(userId);
    }

    public EmailTemplateContent content() {
        return new EmailTemplateContent(name, subject, body);
    }
}
