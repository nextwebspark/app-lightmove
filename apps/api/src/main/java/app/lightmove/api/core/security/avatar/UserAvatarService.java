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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * A person's profile photo — their own upload or removal, and the copy of their provider's. Every write locks the user
 * row, so writes landing together decide one after another; every download happens before that lock.
 */
@Service
public class UserAvatarService {

    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;

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
        transactions.executeWithoutResult(status -> hold(lockEditable(actorId), picture, User.UPLOADED_AVATAR_SOURCE));
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

    /** After a completed sign-in: the provider's picture becomes ours if that provider may still supply it. */
    public void copyFromProvider(UUID userId, String source, String pictureUrl) {
        download(pictureUrl).ifPresent(picture -> transactions.executeWithoutResult(status ->
                users.lockForAvatarChange(userId)
                        .filter(user -> user.mayTakeAvatarFrom(source))
                        .ifPresent(user -> hold(user, picture, source))));
    }

    @Transactional(readOnly = true)
    public Optional<StoredAvatar> read(String token) {
        if (!AvatarLinks.isWellFormedToken(token)) {
            return Optional.empty();
        }
        return avatars.findByToken(token).map(avatar -> new StoredAvatar(avatar.getContent(), avatar.getContentType()));
    }

    private void hold(User user, AvatarPicture picture, String source) {
        UserAvatar avatar = avatars.findByUserId(user.getId())
                .map(held -> {
                    if (!held.holds(picture)) {
                        held.replaceWith(picture);
                    }
                    return held;
                })
                .orElseGet(() -> avatars.save(UserAvatar.of(user.getId(), picture)));
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
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", tooLargeMessage());
        }
        try {
            return file.getBytes();
        } catch (IOException unreadable) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", "Use a JPG or PNG image");
        }
    }

    /** From the configured cap, never the request: a fixed sentence once the deployment is configured. */
    private String tooLargeMessage() {
        long megabytes = Math.max(1, Math.floorDiv(settings.maxUploadBytes(), BYTES_PER_MEGABYTE));
        return "Use an image under %d MB".formatted(megabytes);
    }

    /** Empty where the link answers no picture we can read, now or ever: the person keeps what they had. */
    private Optional<AvatarPicture> download(String pictureUrl) {
        URI url;
        try {
            url = URI.create(pictureUrl);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return fetcher.fetch(url) instanceof PictureFetch.Fetched(byte[] content)
                ? Optional.ofNullable(AvatarImages.normalise(content))
                : Optional.empty();
    }
}
