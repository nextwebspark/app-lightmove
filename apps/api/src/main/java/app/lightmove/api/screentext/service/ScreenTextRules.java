package app.lightmove.api.screentext.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.screentext.model.ScreenTextCatalogEntry;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What an edited sentence must keep to be safe on screen: its placeholders, its length, plain words. */
@Component
@RequiredArgsConstructor
public class ScreenTextRules {

    static final String FIELD = "text";
    private static final Pattern MARKUP = Pattern.compile("<\\s*[/!A-Za-z]");

    private final ScreenTextCatalog catalog;

    public void check(ScreenTextCatalogEntry entry, String text) {
        if (text == null || text.isBlank()) {
            throw refused("Enter the text, or reset it to the default");
        }
        if (text.length() > entry.maxLength()) {
            throw refused("Keep it to %d characters or fewer".formatted(entry.maxLength()));
        }
        if (!ScreenTextCatalog.placeholdersOf(text).equals(entry.placeholders().stream().sorted().toList())) {
            throw refused("Keep every {placeholder} the default has, and add no new ones");
        }
        if (MARKUP.matcher(text).find()) {
            throw refused("Screen text is plain words. Take out the < > tags");
        }
        for (Pattern banned : catalog.neverOnScreen()) {
            if (banned.matcher(text).find() && !banned.matcher(entry.defaultText()).find()) {
                throw refused("That wording uses a word we never show people. See the writing guide");
            }
        }
    }

    private static ApiException refused(String message) {
        return ApiException.withField(ErrorCode.VALIDATION_FAILED, FIELD, message);
    }
}
