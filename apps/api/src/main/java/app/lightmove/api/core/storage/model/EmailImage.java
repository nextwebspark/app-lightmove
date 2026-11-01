package app.lightmove.api.core.storage.model;

import app.lightmove.api.core.storage.constant.EmailImageKind;
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

/**
 * A picture an email shows, served to anyone holding its token. Never changed once written: an email already sent
 * names the token, so a new photo or logo is a new row.
 */
@Entity
@Table(name = "app_lm_email_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailImage {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token", nullable = false, updatable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private EmailImageKind kind;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "source_url_sha256", updatable = false)
    private String sourceUrlSha256;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "width", nullable = false, updatable = false)
    private int width;

    @Column(name = "height", nullable = false, updatable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static EmailImage photoOf(UUID userId, String token, EncodedImage image, String storageKey) {
        EmailImage photo = stored(EmailImageKind.PHOTO, token, image, storageKey);
        photo.userId = userId;
        return photo;
    }

    public static EmailImage logoOf(UUID workspaceId, String sourceUrlSha256, String token, EncodedImage image,
                                    String storageKey) {
        EmailImage logo = stored(EmailImageKind.LOGO, token, image, storageKey);
        logo.workspaceId = workspaceId;
        logo.sourceUrlSha256 = sourceUrlSha256;
        return logo;
    }

    private static EmailImage stored(EmailImageKind kind, String token, EncodedImage image, String storageKey) {
        EmailImage stored = new EmailImage();
        stored.kind = kind;
        stored.token = token;
        stored.contentType = image.contentType();
        stored.width = image.width();
        stored.height = image.height();
        stored.sizeBytes = image.content().length;
        stored.storageKey = storageKey;
        stored.createdAt = Instant.now();
        return stored;
    }
}
