package app.lightmove.api.outreach.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The values a sequence's or an email's {@code {{token}}}s are filled with for one person. {@code {{name|fallback}}}
 * reads the fallback where the person has no value. A token nobody offers is left exactly as typed, so a misspelling
 * shows in the review rather than vanishing from the email.
 */
public record SequenceTokens(String firstName, String currentTitle, String currentCompany, String positionTitle,
                             String location, String senderFirstName, String opener, String bookingLink) {

    public static final String BOOKING_LINK = "bookingLink";
    public static final String OPENER = "opener";

    /**
     * The sender's default signature. {@link #render} leaves it as typed: the signature is HTML, drawn into the email by
     * {@link OutreachEmailBody}, never into the text.
     */
    public static final String SIGNATURE = "signature";

    public static final Pattern SIGNATURE_TOKEN = Pattern.compile("\\{\\{\\s*" + SIGNATURE + "\\s*}}");

    /** The six an email to any executive can take, in the order the Insert bar offers them. */
    public static final List<String> PROFILE_TOKENS = List.of(
            "firstName", "currentTitle", "currentCompany", "positionTitle", "location", "senderFirstName");

    /**
     * {@code {{name}}} or {@code {{name|fallback}}}. The pipe may arrive escaped ({@code \|}): the notes editor's
     * Markdown serializer escapes every pipe in text, so a fallback typed in the composer is stored that way.
     */
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*(?:\\\\?\\|([^{}|\\n]{1,60}))?\\s*}}");

    /** What can open markup inline — a link, an image, an autolink, emphasis, a heading — and nothing a name needs. */
    private static final Pattern MARKDOWN_PUNCTUATION = Pattern.compile("[\\\\`*_\\[\\]<>~#+!]");

    public String render(String template) {
        return renderWith(template, values(), UnaryOperator.identity());
    }

    /**
     * For a body written in Markdown: a value came from a profile, and a profile from the web, so whatever in it could
     * open markup is escaped and it can never become a link, a heading or an image. A fallback is the template author's
     * text in the same body, so it is escaped alike. The booking link is the one anchor, as an autolink.
     */
    public String renderMarkdown(String template) {
        Map<String, String> values = new LinkedHashMap<>();
        values().forEach((name, value) -> values.put(name, BOOKING_LINK.equals(name)
                ? (value.isBlank() ? "" : "<" + value + ">")
                : markdownSafe(value)));
        return renderWith(template, values, SequenceTokens::markdownSafe);
    }

    /** A subject: every value on one line, so a profile value can never break the header. */
    public String renderLine(String template) {
        Map<String, String> values = new LinkedHashMap<>();
        values().forEach((name, value) -> values.put(name, collapsed(value)));
        return collapsed(renderWith(template, values, UnaryOperator.identity()));
    }

    /** The known tokens {@code text} asks for with no value here and no fallback, in order of first use. */
    public List<String> unfilledIn(String text) {
        Map<String, String> values = values();
        Set<String> unfilled = new LinkedHashSet<>();
        for (TokenUse use : usesIn(text)) {
            String value = values.get(use.name());
            if (value != null && value.isBlank() && use.fallback() == null) {
                unfilled.add(use.name());
            }
        }
        return List.copyOf(unfilled);
    }

    /** The tokens {@code text} asks for that are not in {@code vocabulary}, in order of first use. */
    public static List<String> unknownIn(String text, Collection<String> vocabulary) {
        Set<String> unknown = new LinkedHashSet<>();
        for (TokenUse use : usesIn(text)) {
            if (!vocabulary.contains(use.name())) {
                unknown.add(use.name());
            }
        }
        return List.copyOf(unknown);
    }

    /** Every token {@code text} holds, in order, with its fallback trimmed and collapsed (null where none). */
    public static List<TokenUse> usesIn(String text) {
        List<TokenUse> uses = new ArrayList<>();
        if (text == null) {
            return uses;
        }
        Matcher found = TOKEN.matcher(text);
        while (found.find()) {
            uses.add(new TokenUse(found.group(1), fallbackOf(found.group(2))));
        }
        return uses;
    }

    private static String renderWith(String template, Map<String, String> values, UnaryOperator<String> fallbackText) {
        if (template == null) {
            return "";
        }
        Matcher token = TOKEN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (token.find()) {
            String value = values.get(token.group(1));
            String fallback = fallbackOf(token.group(2));
            String filled = value == null ? token.group()
                    : value.isBlank() && fallback != null ? fallbackText.apply(fallback)
                    : value;
            token.appendReplacement(rendered, Matcher.quoteReplacement(filled));
        }
        token.appendTail(rendered);
        return rendered.toString();
    }

    private Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("firstName", orBlank(firstName));
        values.put("currentTitle", orBlank(currentTitle));
        values.put("currentCompany", orBlank(currentCompany));
        values.put("positionTitle", orBlank(positionTitle));
        values.put("location", orBlank(location));
        values.put("senderFirstName", orBlank(senderFirstName));
        values.put(OPENER, orBlank(opener));
        values.put(BOOKING_LINK, orBlank(bookingLink));
        return values;
    }

    /** Whether {@code template} asks for {@code token}, however it is spaced inside the braces. */
    public static boolean uses(String template, String token) {
        return usesIn(template).stream().anyMatch(use -> use.name().equals(token));
    }

    private static String fallbackOf(String typed) {
        if (typed == null) {
            return null;
        }
        String fallback = collapsed(typed);
        return fallback.isEmpty() ? null : fallback;
    }

    private static String markdownSafe(String value) {
        return MARKDOWN_PUNCTUATION.matcher(value.replaceAll("\\s+", " ")).replaceAll("\\\\$0");
    }

    private static String collapsed(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    /**
     * {@code text} for a run signed with {@code signatureHtml}; for one with none, each signature token becomes the
     * sender's first name, so the email still signs off.
     */
    public static String signedOff(String text, String signatureHtml, String senderFirstName) {
        if (text == null || signatureHtml != null) {
            return text == null ? "" : text;
        }
        return SIGNATURE_TOKEN.matcher(text)
                .replaceAll(Matcher.quoteReplacement(senderFirstName == null ? "" : senderFirstName));
    }

    /** A value came from a profile or a model, so it can never place a signature: only the consultant's words do. */
    private static String orBlank(String value) {
        return value == null ? "" : SIGNATURE_TOKEN.matcher(value).replaceAll("").trim();
    }

    /** The first word of a full name, which is what a greeting uses. */
    public static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+", 2)[0];
    }
}
