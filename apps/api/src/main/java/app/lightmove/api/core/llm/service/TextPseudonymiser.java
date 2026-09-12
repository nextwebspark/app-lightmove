package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.Pseudonyms;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The redaction engine every feature pseudonymising text before a model call can share — generic and
 * feature-agnostic, knowing nothing about clients, contacts, or any other domain vocabulary. Sits
 * beside {@link LlmCallPolicy} because it is the other half of "what every call to the model gets,
 * whichever feature makes it".
 *
 * <p><b>Pseudonymisation, not de-identification.</b> It replaces a caller-supplied list of literal
 * terms and regex matches with placeholders, and hands back the vocabulary to re-hydrate them
 * afterwards. It does not attempt to detect anything it was not told about — the domain vocabulary a
 * caller supplies is the whole of what gets redacted, and deciding that vocabulary is the caller's job
 * (see {@code PositionDocumentRedactor}).
 */
@Service
public class TextPseudonymiser {

    private static final Pattern LITERAL_OPEN = Pattern.compile(Pattern.quote("[["));
    private static final Pattern LITERAL_CLOSE = Pattern.compile(Pattern.quote("]]"));

    /** The redacted text and the vocabulary that can put it back — the pair a caller acts on together. */
    public record Redaction(String text, Pseudonyms pseudonyms) {}

    /**
     * Redacts every literal term (case-insensitive, whole-word) and every regex match, minting one
     * placeholder per distinct original value.
     *
     * @param terms    literal strings to redact, keyed by the label their placeholders carry — e.g.
     *                 {@code "COMPANY" -> ["Acme Holdings", "Acme"]}
     * @param patterns regex matches to redact, keyed the same way — e.g. {@code "EMAIL" -> ...}
     */
    public Redaction redact(String text, Map<String, List<String>> terms, Map<String, Pattern> patterns) {
        // Escape first, before anything of our own is minted: a source already containing "[[X]]"
        // must never be indistinguishable from a placeholder this pass mints, or a hostile document
        // could forge its own re-hydration. See Pseudonyms' class doc for the concrete attack.
        String escaped = LITERAL_CLOSE
                .matcher(LITERAL_OPEN.matcher(text).replaceAll(Pseudonyms.ESCAPED_OPEN))
                .replaceAll(Pseudonyms.ESCAPED_CLOSE);

        Map<String, String> placeholderToOriginal = new LinkedHashMap<>();
        String redacted = escaped;
        for (Map.Entry<String, List<String>> entry : terms.entrySet()) {
            redacted = redactTerms(redacted, entry.getKey(), entry.getValue(), placeholderToOriginal);
        }
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            redacted = redactPattern(redacted, entry.getKey(), entry.getValue(), placeholderToOriginal);
        }
        return new Redaction(redacted, new Pseudonyms(placeholderToOriginal));
    }

    private static String redactTerms(String text, String label, List<String> terms,
                                      Map<String, String> placeholderToOriginal) {
        String result = text;
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            // Bounded by "not alphanumeric" rather than \b: a term ending in a non-word character
            // (a trailing "Co.") still needs a real boundary check, and \b's symmetric word/non-word
            // rule misses that case when the character right after the match is also non-word.
            Pattern bounded = Pattern.compile(
                    "(?i)(?<![A-Za-z0-9])" + Pattern.quote(term) + "(?![A-Za-z0-9])");
            result = replaceAll(result, bounded, label, placeholderToOriginal);
        }
        return result;
    }

    private static String redactPattern(String text, String label, Pattern pattern,
                                        Map<String, String> placeholderToOriginal) {
        return replaceAll(text, pattern, label, placeholderToOriginal);
    }

    private static String replaceAll(String text, Pattern pattern, String label,
                                     Map<String, String> placeholderToOriginal) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder rebuilt = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            rebuilt.append(text, last, matcher.start());
            rebuilt.append(placeholderFor(label, matcher.group(), placeholderToOriginal));
            last = matcher.end();
        }
        rebuilt.append(text, last, text.length());
        return rebuilt.toString();
    }

    /** One placeholder per distinct original value — the same term redacted twice mints once. */
    private static String placeholderFor(String label, String original,
                                         Map<String, String> placeholderToOriginal) {
        for (Map.Entry<String, String> entry : placeholderToOriginal.entrySet()) {
            if (entry.getValue().equals(original)) {
                return entry.getKey();
            }
        }
        long alreadyMinted = placeholderToOriginal.keySet().stream()
                .filter(key -> key.startsWith("[[" + label + "_"))
                .count();
        String placeholder = "[[" + label + "_" + (alreadyMinted + 1) + "]]";
        placeholderToOriginal.put(placeholder, original);
        return placeholder;
    }
}
