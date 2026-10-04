package app.lightmove.api.core.text.service;

/**
 * A caller-supplied upload name made safe to echo into an audit detail, a response or a
 * {@code Content-Disposition} header: path separators and control characters that could forge any of
 * those are stripped, and the result is capped at a filesystem's usual 255.
 */
public final class FileNameSanitizer {

    private static final int MAX_LENGTH = 255;

    private FileNameSanitizer() {}

    /** The cleaned name, or {@code fallback} when nothing usable is left. */
    public static String sanitize(String originalFileName, String fallback) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return fallback;
        }
        String withoutPath = originalFileName.replaceAll(".*[/\\\\]", "");
        String cleaned = withoutPath.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
