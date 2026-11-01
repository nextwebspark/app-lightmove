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

/**
 * A person's stored profile photo (V169). The token is a link, not a credential: it opens this one image and nothing
 * else, as a booking link's slug opens one page, so it is kept as it is served. A new picture is a new token.
 */
@Entity
@Table(name = "app_lm_user_avatar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvatar extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 43)
    private String token;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, length = 32)
    private String source;

    static UserAvatar of(UUID userId, AvatarPicture picture, String source) {
        UserAvatar avatar = new UserAvatar();
        avatar.userId = userId;
        avatar.replaceWith(picture, source);
        return avatar;
    }

    boolean holds(AvatarPicture picture) {
        return sha256.equals(picture.sha256());
    }

    void replaceWith(AvatarPicture picture, String source) {
        this.token = Tokens.generate();
        this.content = picture.jpeg();
        this.contentType = AvatarPicture.CONTENT_TYPE;
        this.sha256 = picture.sha256();
        this.source = source;
    }

    /** The same picture again from the same source: the link stays, so nobody's cached copy is thrown away. */
    void reclaimFor(String source) {
        this.source = source;
    }

    String link() {
        return AvatarLinks.linkOf(token);
    }
}
