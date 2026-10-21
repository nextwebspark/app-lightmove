package app.lightmove.api.screentext.constant;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Arrays;

/** The languages screen text is kept in. English only for now; the column is there so another needs no migration. */
public enum ScreenTextLocale {

    EN("en");

    private final String tag;

    ScreenTextLocale(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public static ScreenTextLocale of(String tag) {
        return Arrays.stream(values())
                .filter(locale -> locale.tag.equalsIgnoreCase(tag))
                .findFirst()
                .orElseThrow(() -> ApiException.withField(ErrorCode.VALIDATION_FAILED, "locale",
                        "That language is not offered"));
    }
}
