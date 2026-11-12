package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.outreach.constant.SignatureLayout;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One of a consultant's email signatures in one workspace. Only its owner reads or changes it. */
@Entity
@Table(name = "app_lm_email_signature")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSignature extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout", nullable = false, length = 16)
    private SignatureLayout layout;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", columnDefinition = "jsonb", nullable = false)
    private SignatureFields fields = SignatureFields.NONE;

    @Column(name = "show_photo", nullable = false)
    private boolean showPhoto;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_shape", nullable = false, length = 8)
    private PhotoShape photoShape = PhotoShape.CIRCLE;

    @Column(name = "show_logo", nullable = false)
    private boolean showLogo;

    @Column(name = "show_booking_link", nullable = false)
    private boolean showBookingLink;

    @Column(name = "custom_body", length = 2000)
    private String customBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "footer_pictures", columnDefinition = "jsonb", nullable = false)
    private List<String> footerPictures = new ArrayList<>();

    @Column(name = "disclaimer", length = 1000)
    private String disclaimer;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public static EmailSignature of(UUID workspaceId, UUID userId, SignatureContent content) {
        EmailSignature signature = new EmailSignature();
        signature.workspaceId = workspaceId;
        signature.userId = userId;
        signature.write(content);
        return signature;
    }

    public void write(SignatureContent content) {
        this.name = content.name();
        this.layout = content.layout();
        this.fields = content.fields();
        this.showPhoto = content.showPhoto();
        this.photoShape = content.photoShape();
        this.showLogo = content.showLogo();
        this.showBookingLink = content.showBookingLink();
        this.customBody = content.customBody();
        this.footerPictures = new ArrayList<>(content.footerPictures());
        this.disclaimer = content.disclaimer();
    }

    public SignatureContent content() {
        return new SignatureContent(name, layout, fields == null ? SignatureFields.NONE : fields, showPhoto, photoShape,
                showLogo, showBookingLink, customBody, footerPictures, disclaimer);
    }

    public void markDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
