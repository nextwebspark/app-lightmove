package app.uncava.api.core.llm.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The email and phone patterns every redaction before a model call shares, and the redaction itself for free text a
 * person wrote — a note, a CV, a profile's About. One copy, so two redactors cannot drift apart as they once did.
 */
public final class ContactRedaction {

    public static final String EMAIL_REMOVED = "[email removed]";
    public static final String PHONE_REMOVED = "[phone removed]";

    public static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * At least eight characters of digits, dashes, spaces, tabs or parentheses — never a dot, too easily a decimal or
     * a dated month, and never a line break, which would join a phone to a date on the next line — not preceded by a
     * currency. The currency exclusion is load-bearing: without it "AED 1,200,000" reads as a phone.
     */
    public static final Pattern PHONE_CANDIDATE = Pattern.compile(
            "(?<!(?i:AED|USD|SAR|QAR|KWD|BHD|OMR|GBP|EUR)[ \\t]{0,4})(?<![$€£][ \\t]{0,4})"
                    + "\\+?\\d[\\d\\- \\t()]{6,}\\d");

    private static final String DATED = "(?:\\d{1,2}[-/]){0,2}\\d{4}(?:[-/]\\d{1,2}){0,2}";
    private static final Pattern DATE_OR_RANGE = Pattern.compile(DATED + "(?:[ \\t]*[-–][ \\t]*" + DATED + ")?");
    private static final Pattern PHONE_LABEL =
            Pattern.compile("(?i)(?:\\b(?:tel|phone|mobile|mob|cell|whatsapp)\\b\\.?|\\b[mt]:)[ \\t:.-]*$");
    private static final int LABEL_REACH = 16;
    private static final int MIN_PHONE_DIGITS = 8;
    private static final int UNMARKED_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;

    private ContactRedaction() {}

    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String withoutEmails = EMAIL.matcher(text).replaceAll(EMAIL_REMOVED);
        Matcher candidates = PHONE_CANDIDATE.matcher(withoutEmails);
        StringBuilder redacted = new StringBuilder();
        while (candidates.find()) {
            String before = withoutEmails.substring(Math.max(0, candidates.start() - LABEL_REACH), candidates.start());
            String found = candidates.group();
            candidates.appendReplacement(redacted,
                    Matcher.quoteReplacement(isPhone(found, before) ? PHONE_REMOVED : found));
        }
        candidates.appendTail(redacted);
        return redacted.toString();
    }

    /**
     * Ten digits or more, or a leading +, is a phone; a date or a date range never is. Eight or nine digits read as a
     * phone only when dialled like one or labelled as one — a figure that long is more often money.
     */
    private static boolean isPhone(String found, String before) {
        String trimmed = found.strip();
        long digits = trimmed.chars().filter(Character::isDigit).count();
        if (digits < MIN_PHONE_DIGITS || digits > MAX_PHONE_DIGITS) {
            return false;
        }
        if (trimmed.startsWith("+")) {
            return true;
        }
        if (DATE_OR_RANGE.matcher(trimmed).matches()) {
            return false;
        }
        return digits >= UNMARKED_PHONE_DIGITS || trimmed.startsWith("0") || trimmed.startsWith("(")
                || PHONE_LABEL.matcher(before).find();
    }
}
