package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What a person's document is. Held to the same list by V104's CHECK. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PersonDocumentCategory implements ApiValueEnum {

    CV("cv"),
    COVER_LETTER("cover_letter"),
    REFERENCE("reference"),
    CERTIFICATE("certificate"),
    ASSESSMENT("assessment"),
    OTHER("other");

    private static final Pattern CV_WORDS = Pattern.compile("(^|[^a-z])(cv|resume|résumé|curriculum)([^a-z]|$)");
    private static final Pattern COVER_WORDS = Pattern.compile("cover|motivation");
    private static final Pattern REFERENCE_WORDS = Pattern.compile("referen|recommendation");
    private static final Pattern CERTIFICATE_WORDS = Pattern.compile("certificat|diploma|degree|transcript");
    private static final Pattern ASSESSMENT_WORDS = Pattern.compile("assessment|psychometric|hogan");

    private final String value;

    /** A first guess from the file name, for an upload that named no category; the researcher can change it. */
    public static PersonDocumentCategory guessFrom(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (COVER_WORDS.matcher(name).find()) {
            return COVER_LETTER;
        }
        if (REFERENCE_WORDS.matcher(name).find()) {
            return REFERENCE;
        }
        if (CERTIFICATE_WORDS.matcher(name).find()) {
            return CERTIFICATE;
        }
        if (ASSESSMENT_WORDS.matcher(name).find()) {
            return ASSESSMENT;
        }
        if (CV_WORDS.matcher(name).find()) {
            return CV;
        }
        return OTHER;
    }
}
