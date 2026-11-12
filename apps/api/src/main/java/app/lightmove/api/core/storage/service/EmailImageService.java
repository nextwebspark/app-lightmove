package app.lightmove.api.core.storage.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.avatar.PictureFetch;
import app.lightmove.api.core.security.avatar.PictureFetcher;
import app.lightmove.api.core.security.avatar.UserAvatar;
import app.lightmove.api.core.security.avatar.UserAvatarRepository;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.storage.constant.EmailImageKind;
import app.lightmove.api.core.storage.model.EmailImage;
import app.lightmove.api.core.storage.model.EncodedImage;
import app.lightmove.api.core.storage.repository.EmailImageRepository;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The pictures an email carries: a copy of a person's profile photo and of a workspace's logo, each made the first time
 * a signature draws it, and the pictures a person uploads into their Custom signature. A recipient's mail client
 * fetches them with no session, so each is served publicly by a random token ({@code /api/v1/email-images/{token}}) —
 * one per stored picture, shared by every email showing it and never minted per message or recipient, so a fetch
 * identifies nobody — never a signed bucket URL, which would expire in an inbox, and never another host's address.
 * Nothing here runs inside a transaction: the bytes go to the bucket first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailImageService {

    public static final String PATH = "/api/v1/email-images/";

    /** A logo that could not be copied is not asked for again on every preview. */
    private static final Duration FAILED_LOGO_RETRY_AFTER = Duration.ofMinutes(10);

    private final EmailImageRepository images;
    private final TenantResolvers resolvers;
    private final UserAvatarRepository avatars;
    private final DocumentStore store;
    private final PictureFetcher fetcher;
    private final LightMoveProperties properties;
    private final Map<String, Instant> failedLogos = new ConcurrentHashMap<>();

    /**
     * The person's profile photo as an email draws it, copied once per photo; empty where they have none. A photo they
     * replace or remove later stays in the emails that already carry it.
     */
    public Optional<EmailImage> photoOf(UUID userId) {
        Optional<UserAvatar> avatar = avatars.findByUserId(userId);
        if (avatar.isEmpty()) {
            return Optional.empty();
        }
        String sourceHash = avatar.get().getSha256();
        Supplier<Optional<EmailImage>> copied = () -> images.findByKindAndUserIdAndSourceSha256(EmailImageKind.PHOTO,
                userId, sourceHash);
        if (copied.get().isPresent()) {
            return copied.get();
        }
        return EmailImageEncoder.photo(avatar.get().getContent())
                .flatMap(photo -> save("users/" + userId + "/email-images/", photo, copied,
                        key -> EmailImage.photoOf(userId, sourceHash, Tokens.generate(), photo, key)));
    }

    /**
     * The workspace's logo, copied in from where it is published the first time it is asked for, then answered from the
     * copy. Empty where there is none, or it could not be fetched or read — a signature then goes without it.
     */
    public Optional<EmailImage> logoOf(UUID workspaceId, String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return Optional.empty();
        }
        String sourceHash = Tokens.hash(logoUrl.trim());
        Supplier<Optional<EmailImage>> copied = () -> images.findByKindAndWorkspaceIdAndSourceSha256(EmailImageKind.LOGO,
                workspaceId, sourceHash);
        if (copied.get().isPresent()) {
            return copied.get();
        }
        String failureKey = workspaceId + ":" + sourceHash;
        Instant failedAt = failedLogos.get(failureKey);
        if (failedAt != null && failedAt.plus(FAILED_LOGO_RETRY_AFTER).isAfter(Instant.now())) {
            return Optional.empty();
        }
        Optional<EncodedImage> logo = addressOf(logoUrl).map(fetcher::fetch)
                .flatMap(fetched -> fetched instanceof PictureFetch.Fetched picture
                        ? EmailImageEncoder.logo(picture.content()) : Optional.empty());
        if (logo.isEmpty()) {
            failedLogos.put(failureKey, Instant.now());
            return Optional.empty();
        }
        failedLogos.remove(failureKey);
        return save("ws/" + workspaceId + "/email-images/", logo.get(), copied,
                key -> EmailImage.logoOf(workspaceId, sourceHash, Tokens.generate(), logo.get(), key));
    }

    /**
     * A picture a person put in their Custom signature, kept once per file however often it is uploaded. Empty for
     * anything that is not a JPEG or PNG this server can read safely.
     */
    public Optional<EmailImage> uploadSignaturePicture(UUID workspaceId, UUID userId, byte[] content) {
        String contentHash = sha256(content);
        Supplier<Optional<EmailImage>> copied = () -> images.findByKindAndWorkspaceIdAndUserIdAndSourceSha256(
                EmailImageKind.SIGNATURE, workspaceId, userId, contentHash);
        if (copied.get().isPresent()) {
            return copied.get();
        }
        return EmailImageEncoder.signaturePicture(content)
                .flatMap(picture -> save("ws/" + workspaceId + "/users/" + userId + "/email-images/", picture, copied,
                        key -> EmailImage.signaturePictureOf(workspaceId, userId, contentHash, Tokens.generate(),
                                picture, key)));
    }

    /**
     * Of {@code tokens}, the pictures this person may show in a signature here: their own uploads in this workspace,
     * their own photo, and this workspace's logo. Any other — a colleague's upload or photo — is absent.
     */
    public List<EmailImage> signaturePicturesOf(UUID workspaceId, UUID userId, Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        return images.findByTokenIn(tokens).stream().filter(image -> switch (image.getKind()) {
            case SIGNATURE -> workspaceId.equals(image.getWorkspaceId()) && userId.equals(image.getUserId());
            case PHOTO -> userId.equals(image.getUserId());
            case LOGO -> workspaceId.equals(image.getWorkspaceId());
        }).toList();
    }

    /** The public route's read: no workspace is known until the token names one. */
    public Optional<EmailImage> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return resolvers.emailImage(token).flatMap(found -> found.workspaceId() == null
                ? images.findByToken(token)
                : WorkspaceScope.call(found.workspaceId(), () -> images.findByToken(token)));
    }

    public InputStream open(EmailImage image) {
        try {
            return store.open(image.getStorageKey());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** Absolute, since a mail client resolves it against nothing. */
    public String urlOf(EmailImage image) {
        return properties.web().baseUrl() + PATH + image.getToken();
    }

    /** Two signatures drawn at once may both copy it: the second row loses on the unique index and reads the first. */
    private Optional<EmailImage> save(String prefix, EncodedImage image, Supplier<Optional<EmailImage>> copied,
                                      Function<String, EmailImage> rowFor) {
        String key = prefix + UUID.randomUUID() + (image.contentType().equals("image/png") ? ".png" : ".jpg");
        try {
            store.put(key, new ByteArrayInputStream(image.content()), image.content().length, image.contentType());
        } catch (IOException failed) {
            log.warn("Storing an email image failed: {}", failed.getMessage());
            return Optional.empty();
        }
        try {
            return Optional.of(images.save(rowFor.apply(key)));
        } catch (DataIntegrityViolationException copiedMeanwhile) {
            deleteQuietly(key);
            return copied.get();
        }
    }

    private void deleteQuietly(String key) {
        try {
            store.delete(key);
        } catch (IOException | RuntimeException failed) {
            log.warn("Could not delete an unused email image {}: {}", key, failed.getMessage());
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static Optional<URI> addressOf(String url) {
        try {
            return Optional.of(URI.create(url.trim()));
        } catch (IllegalArgumentException notAnAddress) {
            return Optional.empty();
        }
    }
}
