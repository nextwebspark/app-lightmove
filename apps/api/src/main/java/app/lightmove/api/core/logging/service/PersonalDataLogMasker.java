package app.lightmove.api.core.logging.service;

import app.lightmove.api.core.text.service.ContactRedaction;
import com.fasterxml.jackson.core.JsonStreamContext;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.logstash.logback.mask.ValueMasker;

/**
 * Masks emails, LinkedIn profile links and dialled phones in every deployed log line's message and stack trace (#927).
 * Built by Logback via {@code logback-spring.xml}, not Spring. A phone needs its leading {@code +}, unlike
 * {@link ContactRedaction#redact}, whose looser rule would mask every line's UUIDs and epoch timestamps.
 */
public class PersonalDataLogMasker implements ValueMasker {

    private static final String PROFILE_REMOVED = "[profile removed]";

    private static final Set<String> MASKED_FIELDS = Set.of("message", "stack_trace");

    /**
     * Written out or percent-encoded as a query value, under {@code /in/} or the older {@code /pub/}. The lookbehind
     * starts a match only where a host name does: unanchored, a long run of word characters is retried from every
     * position in it.
     */
    private static final Pattern LINKEDIN_PROFILE = Pattern.compile("(?i)(?<![a-z0-9.-])"
            + "(?:https?(?::|%3A)(?://|%2F%2F))?(?:[a-z0-9-]+\\.)*linkedin\\.com(?:/|%2F)(?:in|pub)(?:/|%2F)"
            + "[^\\s/?#&\"'<>]+/?");

    private static final Pattern DIALLED_PHONE = Pattern.compile("(?<![\\w.+])\\+\\d[\\d\\- \\t()]{6,}\\d");

    private static final int MIN_PHONE_DIGITS = 8;

    /** {@code null} leaves the value as it is, which is what Logback's encoder expects when nothing was masked. */
    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (!(value instanceof String text) || !MASKED_FIELDS.contains(context.getCurrentName())) {
            return null;
        }
        String masked = redact(text);
        return masked.equals(text) ? null : masked;
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = text;
        if (containsIgnoringCase(masked, "linkedin")) {
            masked = LINKEDIN_PROFILE.matcher(masked).replaceAll(PROFILE_REMOVED);
        }
        if (masked.indexOf('@') >= 0) {
            masked = ContactRedaction.EMAIL.matcher(masked).replaceAll(ContactRedaction.EMAIL_REMOVED);
        }
        return masked.indexOf('+') >= 0 ? withoutDialledPhones(masked) : masked;
    }

    private static boolean containsIgnoringCase(String text, String word) {
        for (int at = 0; at <= text.length() - word.length(); at++) {
            if (text.regionMatches(true, at, word, 0, word.length())) {
                return true;
            }
        }
        return false;
    }

    private static String withoutDialledPhones(String text) {
        Matcher found = DIALLED_PHONE.matcher(text);
        StringBuilder masked = new StringBuilder();
        while (found.find()) {
            // No upper bound: the class crosses spaces, so a phone followed by a year or a count reads as one long
            // number, and a cap on the digits handed that phone back unmasked.
            long digits = found.group().chars().filter(Character::isDigit).count();
            boolean isPhone = digits >= MIN_PHONE_DIGITS;
            found.appendReplacement(masked,
                    Matcher.quoteReplacement(isPhone ? ContactRedaction.PHONE_REMOVED : found.group()));
        }
        found.appendTail(masked);
        return masked.toString();
    }
}
