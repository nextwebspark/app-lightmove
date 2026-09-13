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
     * <p>Both maps are typed {@link SequencedMap} rather than {@code Map}, deliberately: redaction is
     * order-sensitive wherever patterns can overlap (an unredacted URL can still contain digits a
     * phone pattern would otherwise consume), and a plain {@code Map.of(...)} does not implement
     * {@code SequencedMap} — so a caller cannot pass one and get away with an order the JVM salted at
     * random. Pass a {@link LinkedHashMap}.
     *
     * @param terms    literal strings to redact, keyed by the label their placeholders carry — e.g.
     *                 {@code "COMPANY" -> ["Acme Holdings", "Acme"]}
     * @param patterns regex matches to redact, keyed the same way — e.g. {@code "EMAIL" -> ...} —
     *                 applied after every entry in {@code terms}, in the order given
     */
    public Redaction redact(String text, SequencedMap<String, List<String>> terms,
                            SequencedMap<String, Pattern> patterns) {
        // Escape first, before anything of our own is minted: a source already containing "[[X]]"
        // must never be indistinguishable from a placeholder this pass mints, or a hostile document
        // could forge its own re-hydration. See Pseudonyms' class doc for the concrete attack.
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

    /**
     * The bookkeeping one {@link #redact} call mints, local to that call: the placeholder vocabulary
     * itself, plus a reverse lookup and a per-label counter so minting a placeholder stays O(1)
     * instead of re-scanning every placeholder minted so far for every match.
     */
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
            // Bounded by "not alphanumeric" rather than \b: a term ending in a non-word character
            // (a trailing "Co.") still needs a real boundary check, and \b's symmetric word/non-word
            // rule misses that case when the character right after the match is also non-word.
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

    /** One placeholder per distinct original value — the same term redacted twice mints once. */
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
