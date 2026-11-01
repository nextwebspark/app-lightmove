package app.lightmove.api.core.security.avatar;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.AvatarSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * A person's profile photo: their own upload or removal, and the copy of what their identity provider sent. The
 * caller's own row, taken from the principal — no RBAC action applies, as for {@code PATCH /auth/me}.
 *
 * <p>Every write locks the user row first, so an upload, a removal and a provider's copy landing together decide one
 * after another; and every download happens before that lock, never inside a transaction.
 */
@Slf4j
@Service
public class UserAvatarService {

    private final UserRepository users;
    private final UserAvatarRepository avatars;
    private final PictureFetcher fetcher;
    private final AuditService audit;
    private final RateLimitGuard rateLimit;
    private final TransactionTemplate transactions;
    private final AvatarSettings settings;

    public UserAvatarService(UserRepository users, UserAvatarRepository avatars, PictureFetcher fetcher,
                             AuditService audit, RateLimitGuard rateLimit, PlatformTransactionManager transactionManager,
                             LightMoveProperties properties) {
        this.users = users;
        this.avatars = avatars;
        this.fetcher = fetcher;
        this.audit = audit;
        this.rateLimit = rateLimit;
        // A transaction of its own: a provider's copy runs from a sign-in's after-commit callback, where joining the
        // sign-in's finished transaction would write nothing (and a row lock has no transaction to hold it).
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.settings = properties.avatars();
    }

    public void upload(UUID actorId, UUID workspaceId, MultipartFile file, HttpServletRequest request) {
        rateLimit.checkAvatarChange(actorId, request);
        AvatarPicture picture = AvatarImages.normalise(contentOf(file));
        if (picture == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", "Use a JPG or PNG image");
        }
        transactions.executeWithoutResult(status -> {
            User user = lockEditable(actorId);
            hold(user, picture, User.UPLOADED_AVATAR_SOURCE);
        });
        audit.event(AuthEventType.AVATAR_UPLOADED).actor(actorId).workspace(workspaceId).from(request).record();
    }

    public void remove(UUID actorId, UUID workspaceId, HttpServletRequest request) {
        rateLimit.checkAvatarChange(actorId, request);
        transactions.executeWithoutResult(status -> {
            User user = lockEditable(actorId);
            avatars.findByUserId(actorId).ifPresent(avatars::delete);
            user.removeAvatar();
        });
        audit.event(AuthEventType.AVATAR_REMOVED).actor(actorId).workspace(workspaceId).from(request).record();
    }

    /**
     * After a sign-in: the provider's picture becomes ours, if that provider may still supply it once downloaded. A
     * link that answers nothing changes nothing — the person keeps whatever they had.
     */
    public void copyFromProvider(UUID userId, String source, String pictureUrl) {
        if (!(download(pictureUrl) instanceof Downloaded(AvatarPicture picture))) {
            return;
        }
        transactions.executeWithoutResult(status -> users.lockForAvatarChange(userId)
                .filter(user -> user.mayTakeAvatarFrom(source))
                .ifPresent(user -> hold(user, picture, source)));
    }

    /**
     * A provider's link stored before V169, copied while it still answers; one that never will is cleared, since a dead
     * link held by one provider kept every other from filling the picture. Nothing happens unless the person still
     * holds exactly that link when the answer is in.
     */
    public void copyLegacyLink(UUID userId, String pictureUrl) {
        Download answer = download(pictureUrl);
        if (answer instanceof NotYet) {
            return;
        }
        transactions.executeWithoutResult(status -> users.lockForAvatarChange(userId)
                .filter(user -> pictureUrl.equals(user.getAvatarUrl()))
                .ifPresent(user -> {
                    if (answer instanceof Downloaded(AvatarPicture picture)) {
                        hold(user, picture, user.getAvatarSource());
                    } else {
                        user.forgetDeadAvatarLink();
                    }
                }));
    }

    @Transactional(readOnly = true)
    public Optional<StoredAvatar> read(String token) {
        if (!AvatarLinks.isWellFormedToken(token)) {
            return Optional.empty();
        }
        return avatars.findByToken(token).map(avatar -> new StoredAvatar(avatar.getContent(), avatar.getContentType()));
    }

    /** A source the person never chose (a pre-V25 row's null) is filed as one we cannot name. */
    private void hold(User user, AvatarPicture picture, String source) {
        String filedAs = source == null ? "UNKNOWN" : source;
        UserAvatar avatar = avatars.findByUserId(user.getId())
                .map(held -> {
                    if (held.holds(picture)) {
                        held.reclaimFor(filedAs);
                    } else {
                        held.replaceWith(picture, filedAs);
                    }
                    return held;
                })
                .orElseGet(() -> avatars.save(UserAvatar.of(user.getId(), picture, filedAs)));
        user.showAvatar(source, avatar.link());
    }

    private User lockEditable(UUID userId) {
        User user = users.lockForAvatarChange(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!user.isAvatarEditable()) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private byte[] contentOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", "Choose a photo");
        }
        if (file.getSize() > settings.maxUploadBytes()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", "Use an image under 5 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException unreadable) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", "Use a JPG or PNG image");
        }
    }

    private Download download(String pictureUrl) {
        URI url;
        try {
            url = URI.create(pictureUrl);
        } catch (IllegalArgumentException malformed) {
            return new NeverAnswers();
        }
        return switch (fetcher.fetch(url)) {
            case PictureFetch.Fetched(byte[] content) -> {
                AvatarPicture picture = AvatarImages.normalise(content);
                yield picture == null ? new NeverAnswers() : new Downloaded(picture);
            }
            case PictureFetch.Gone gone -> new NeverAnswers();
            case PictureFetch.Unavailable unavailable -> new NotYet();
        };
    }

    private sealed interface Download {
    }

    private record Downloaded(AvatarPicture picture) implements Download {
    }

    private record NeverAnswers() implements Download {
    }

    private record NotYet() implements Download {
    }
}
