package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.Pseudonyms;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Replaces caller-supplied terms and regex matches with placeholders before a model call, returning the
 * vocabulary to re-hydrate them. Pseudonymisation, not de-identification: it detects nothing it was not
 * told about.
 */
@Service
public class TextPseudonymiser {

    private static final Pattern LITERAL_OPEN = Pattern.compile(Pattern.quote("[["));
    private static final Pattern LITERAL_CLOSE = Pattern.compile(Pattern.quote("]]"));

    public record Redaction(String text, Pseudonyms pseudonyms) {}

    /**
     * Terms match case-insensitive and whole-word. {@link SequencedMap}, deliberately: overlapping
     * patterns are order-sensitive, and {@code Map.of} would salt the order at random.
     *
     * @param terms    keyed by placeholder label, e.g. {@code "COMPANY" -> ["Acme Holdings", "Acme"]}
     * @param patterns applied after every term, in the order given
     */
    public Redaction redact(String text, SequencedMap<String, List<String>> terms,
                            SequencedMap<String, Pattern> patterns) {
        // Escape first: a source already containing "[[X]]" could otherwise forge its own re-hydration
        // (see Pseudonyms).
        String escaped = LITERAL_CLOSE
                .matcher(LITERAL_OPEN.matcher(text).replaceAll(Pseudonyms.ESCAPED_OPEN))
                .replaceAll(Pseudonyms.ESCAPED_CLOSE);

        RedactionState state = new RedactionState();
        String redacted = escaped;
        for (Map.Entry<String, List<String>> entry : terms.entrySet()) {
            redacted = redactTerms(redacted, entry.getKey(), entry.getValue(), state);
        }
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            redacted = redactPattern(redacted, entry.getKey(), entry.getValue(), state);
        }
        return new Redaction(redacted, new Pseudonyms(state.placeholderToOriginal));
    }

    /** One {@link #redact} call's vocabulary, with a reverse lookup and per-label counter to keep minting O(1). */
    private static final class RedactionState {
        private final Map<String, String> placeholderToOriginal = new LinkedHashMap<>();
        private final Map<String, String> originalToPlaceholder = new HashMap<>();
        private final Map<String, Integer> mintedPerLabel = new HashMap<>();
    }

    private static String redactTerms(String text, String label, List<String> terms, RedactionState state) {
        String result = text;
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            // Not \b: a term ending in a non-word character ("Co.") defeats \b's word/non-word rule.
            Pattern bounded = Pattern.compile(
                    "(?i)(?<![A-Za-z0-9])" + Pattern.quote(term) + "(?![A-Za-z0-9])");
            result = replaceAll(result, bounded, label, state);
        }
        return result;
    }

    private static String redactPattern(String text, String label, Pattern pattern, RedactionState state) {
        return replaceAll(text, pattern, label, state);
    }

    private static String replaceAll(String text, Pattern pattern, String label, RedactionState state) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder rebuilt = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            rebuilt.append(text, last, matcher.start());
            rebuilt.append(placeholderFor(label, matcher.group(), state));
            last = matcher.end();
        }
        rebuilt.append(text, last, text.length());
        return rebuilt.toString();
    }

    private static String placeholderFor(String label, String original, RedactionState state) {
        String existing = state.originalToPlaceholder.get(original);
        if (existing != null) {
            return existing;
        }
        int minted = state.mintedPerLabel.merge(label, 1, Integer::sum);
        String placeholder = "[[" + label + "_" + minted + "]]";
        state.placeholderToOriginal.put(placeholder, original);
        state.originalToPlaceholder.put(original, placeholder);
        return placeholder;
    }
}
