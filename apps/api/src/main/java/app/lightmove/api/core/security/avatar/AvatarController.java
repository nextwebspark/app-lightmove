package app.lightmove.api.core.security.avatar;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A stored profile photo, public: a plain {@code <img>} carries no bearer token, so the link's token is the guard. The
 * same 404 for a malformed token and an unknown one.
 */
@RestController
@RequiredArgsConstructor
class AvatarController {

    private final UserAvatarService avatars;
    private final RateLimitGuard rateLimit;

    @GetMapping(AvatarLinks.PREFIX + "{token}")
    ResponseEntity<byte[]> avatar(@PathVariable String token, HttpServletRequest request) {
        rateLimit.checkAvatarRead(request);
        StoredAvatar avatar = avatars.read(token).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                // Private: each browser asks once per token, but no shared cache keeps a photo its owner removed.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                // Opened on its own, it is a document of this origin: it may run nothing and reach nothing.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(avatar.content());
    }
}
