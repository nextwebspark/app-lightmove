package app.lightmove.api.core.storage.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.storage.constant.EmailImageKind;
import app.lightmove.api.core.storage.model.EmailImage;
import app.lightmove.api.core.storage.model.EncodedImage;
import app.lightmove.api.core.storage.repository.EmailImageRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pictures an email carries. A recipient's mail client fetches them with no session, so each is served publicly by
 * a random token ({@code /api/v1/email-images/{token}}) — never a signed bucket URL, which would expire in an inbox, and
 * never another host's address, which would break with it and tell that host who opened the email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailImageService {

    public static final String PATH = "/api/v1/email-images/";

    /** The photo upload's own ceiling, under the container's multipart cap. */
    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    /** A logo that could not be copied is not asked for again on every preview. */
    private static final Duration FAILED_LOGO_RETRY_AFTER = Duration.ofMinutes(10);

    private final EmailImageRepository images;
    private final DocumentStore store;
    private final RemoteImageFetcher fetcher;
    private final LightMoveProperties properties;
    private final Map<String, Instant> failedLogos = new ConcurrentHashMap<>();

    @Transactional
    public EmailImage storePhoto(UUID userId, byte[] content) {
        if (content.length > MAX_UPLOAD_BYTES) {
            throw ApiException.of(ErrorCode.EMAIL_IMAGE_TOO_LARGE);
        }
        EncodedImage photo = EmailImageEncoder.photo(content)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_IMAGE_UNREADABLE));
        String key = put("users/" + userId + "/email-images/", photo);
        return images.save(EmailImage.photoOf(userId, Tokens.generate(), photo, key));
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
        Optional<EmailImage> copied = images.findByKindAndWorkspaceIdAndSourceUrlSha256(EmailImageKind.LOGO,
                workspaceId, sourceHash);
        if (copied.isPresent()) {
            return copied;
        }
        String failureKey = workspaceId + ":" + sourceHash;
        Instant failedAt = failedLogos.get(failureKey);
        if (failedAt != null && failedAt.plus(FAILED_LOGO_RETRY_AFTER).isAfter(Instant.now())) {
            return Optional.empty();
        }
        Optional<EncodedImage> logo = addressOf(logoUrl).flatMap(fetcher::fetch)
                .flatMap(fetched -> EmailImageEncoder.logo(fetched.content()));
        if (logo.isEmpty()) {
            failedLogos.put(failureKey, Instant.now());
            return Optional.empty();
        }
        failedLogos.remove(failureKey);
        String key = put("ws/" + workspaceId + "/email-images/", logo.get());
        try {
            return Optional.of(images.save(EmailImage.logoOf(workspaceId, sourceHash, Tokens.generate(), logo.get(), key)));
        } catch (DataIntegrityViolationException copiedMeanwhile) {
            deleteQuietly(key);
            return images.findByKindAndWorkspaceIdAndSourceUrlSha256(EmailImageKind.LOGO, workspaceId, sourceHash);
        }
    }

    @Transactional(readOnly = true)
    public Optional<EmailImage> find(String token) {
        return token == null || token.isBlank() ? Optional.empty() : images.findByToken(token);
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

    private String put(String prefix, EncodedImage image) {
        String key = prefix + UUID.randomUUID() + (image.contentType().equals("image/png") ? ".png" : ".jpg");
        try {
            store.put(key, new ByteArrayInputStream(image.content()), image.content().length, image.contentType());
        } catch (IOException failed) {
            throw new UncheckedIOException("Storing an email image failed", failed);
        }
        return key;
    }

    private void deleteQuietly(String key) {
        try {
            store.delete(key);
        } catch (IOException | RuntimeException failed) {
            log.warn("Could not delete an unused email image {}: {}", key, failed.getMessage());
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
