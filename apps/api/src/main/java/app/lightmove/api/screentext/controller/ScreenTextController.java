package app.lightmove.api.screentext.controller;

import app.lightmove.api.screentext.constant.ScreenTextLocale;
import app.lightmove.api.screentext.dto.ScreenTextBundle;
import app.lightmove.api.screentext.service.ScreenTextBundles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * The published screen text, for every page load. Public, because the sign-in screen reads it before anyone is
 * signed in, and it holds nothing but the words the screens already show. Revalidated by ETag, so an unchanged
 * bundle costs a 304.
 */
@RestController
@RequiredArgsConstructor
public class ScreenTextController {

    private final ScreenTextBundles bundles;

    @GetMapping("/api/v1/screen-text")
    public ResponseEntity<ScreenTextBundle> published(@RequestParam(defaultValue = "en") String locale,
                                                      WebRequest request) {
        ScreenTextBundle bundle = bundles.published(ScreenTextLocale.of(locale));
        String etag = "\"" + bundle.version() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(bundle);
    }
}
