package app.lightmove.api.core.storage.controller;

import app.lightmove.api.core.storage.model.EmailImage;
import app.lightmove.api.core.storage.service.EmailImageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A picture an email shows, fetched by whoever opened it — public, the token its only credential. Nothing about a fetch
 * is recorded or counted: this is never an open-tracking pixel. Nor is it rate-limited per address, since Gmail's and
 * Outlook's image proxies fetch for every one of their users from a few shared ones; a picture never changes under its
 * token, so a proxy caches it for good.
 */
@RestController
@RequiredArgsConstructor
public class EmailImageController {

    private final EmailImageService images;

    @GetMapping(EmailImageService.PATH + "{token}")
    public ResponseEntity<InputStreamResource> image(@PathVariable String token) {
        EmailImage image = images.find(token).orElse(null);
        if (image == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .contentLength(image.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(new InputStreamResource(images.open(image)));
    }
}
