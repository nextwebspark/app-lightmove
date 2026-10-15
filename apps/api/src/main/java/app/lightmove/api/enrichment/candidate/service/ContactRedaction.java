package app.lightmove.api.enrichment.candidate.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes email addresses and phone numbers out of free text before it reaches a model: notes and CVs carry them,
 * and the model never needs a way to reach anyone. A date range or a year is not a phone number.
 */
final class ContactRedaction {

    static final String EMAIL_REMOVED = "[email removed]";
    static final String PHONE_REMOVED = "[phone removed]";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_LIKE = Pattern.compile("\\+?\\(?\\d[\\d\\s().-]{6,}\\d");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\d{4}\\s*[-–.]\\s*\\d{4}");
    private static final Pattern DATE = Pattern.compile("\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}");
    private static final int MIN_PHONE_DIGITS = 8;
    private static final int UNMARKED_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;

    private ContactRedaction() {}

    static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String withoutEmails = EMAIL.matcher(text).replaceAll(EMAIL_REMOVED);
        Matcher candidates = PHONE_LIKE.matcher(withoutEmails);
        StringBuilder redacted = new StringBuilder();
        while (candidates.find()) {
            String found = candidates.group();
            candidates.appendReplacement(redacted, Matcher.quoteReplacement(isPhone(found) ? PHONE_REMOVED : found));
        }
        candidates.appendTail(redacted);
        return redacted.toString();
    }

    /** Eight or nine digits read as a phone only when dialled like one; a figure that long is more often money. */
    private static boolean isPhone(String found) {
        String trimmed = found.strip();
        long digits = trimmed.chars().filter(Character::isDigit).count();
        if (digits < MIN_PHONE_DIGITS || digits > MAX_PHONE_DIGITS) {
            return false;
        }
        if (trimmed.startsWith("+")) {
            return true;
        }
        if (YEAR_RANGE.matcher(trimmed).matches() || DATE.matcher(trimmed).matches()) {
            return false;
        }
        return digits >= UNMARKED_PHONE_DIGITS || trimmed.startsWith("0") || trimmed.startsWith("(");
    }
}
