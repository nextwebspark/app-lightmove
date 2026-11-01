package app.lightmove.api.core.security.avatar;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.security.token.Tokens;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A person's stored profile photo (V170); a new picture is a new token, so a link never shows another picture. */
@Entity
@Table(name = "app_lm_user_avatar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvatar extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Kept as served, the documented exception to hashing tokens: it is the link every colleague's screen draws. */
    @Column(nullable = false, length = 43)
    private String token;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    static UserAvatar of(UUID userId, AvatarPicture picture) {
        UserAvatar avatar = new UserAvatar();
        avatar.userId = userId;
        avatar.replaceWith(picture);
        return avatar;
    }

    boolean holds(AvatarPicture picture) {
        return sha256.equals(picture.sha256());
    }

    void replaceWith(AvatarPicture picture) {
        this.token = Tokens.generate();
        this.content = picture.jpeg();
        this.contentType = AvatarPicture.CONTENT_TYPE;
        this.sha256 = picture.sha256();
    }

    String link() {
        return AvatarLinks.linkOf(token);
    }
}
