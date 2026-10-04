package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;
import app.lightmove.api.position.model.ExtractedField;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Turns one raw model-answered value into an {@link ExtractedField}: re-hydration, verification and
 * truncation. {@code haystack} is the whole document, {@link #haystackOf normalised} once per reading
 * rather than per field.
 */
@Service
@Slf4j
public class ExtractedFieldReader {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    String haystackOf(String originalText) {
        return normaliseWhitespace(originalText).toLowerCase(Locale.ROOT);
    }

    /**
     * Drops the field on a surviving redaction placeholder, and drops a snippet that is not a literal
     * quote of {@code haystack} with its confidence. Empty for a blank {@code rawValue}.
     */
    Optional<ExtractedField> fieldFrom(String extractorLabel, String fieldKey, String rawValue,
                                       String rawSnippet, Pseudonyms pseudonyms, String haystack) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = pseudonyms.rehydrate(rawValue);
        String snippet = rawSnippet == null || rawSnippet.isBlank() ? null : pseudonyms.rehydrate(rawSnippet);
        if (pseudonyms.hasResidue(value) || pseudonyms.hasResidue(snippet)) {
            log.warn("{} dropped a {} field: a placeholder survived re-hydration.", extractorLabel, fieldKey);
            return Optional.empty();
        }
        ExtractedField field = new ExtractedField(fieldKey, value.trim(), ProposalConfidence.MEDIUM, snippet,
                ProposalOrigin.DOCUMENT);
        if (snippet != null && !occursIn(snippet, haystack)) {
            field = field.withoutSnippet(ProposalConfidence.LOW);
        }
        return Optional.of(field);
    }

    /** Never {@code Enum.valueOf}: a token the enum does not carry is dropped rather than thrown. */
    <T extends Enum<T>> Optional<ExtractedField> enumFieldFrom(String extractorLabel, String fieldKey,
                                                                Class<T> type, String rawValue, String rawSnippet,
                                                                Pseudonyms pseudonyms, String haystack) {
        return fieldFrom(extractorLabel, fieldKey, rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            T resolved = enumFromName(type, field.value());
            return resolved == null
                    ? Optional.empty()
                    : Optional.of(new ExtractedField(fieldKey, resolved.name(), field.confidence(),
                            field.snippet(), field.origin()));
        });
    }

    <T extends Enum<T>> T enumFromName(Class<T> type, String token) {
        for (T value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(token.trim())) {
                return value;
            }
        }
        return null;
    }

    boolean occursIn(String snippet, String haystack) {
        String normalisedSnippet = normaliseWhitespace(snippet).toLowerCase(Locale.ROOT);
        return !normalisedSnippet.isEmpty() && haystack.contains(normalisedSnippet);
    }

    String normaliseWhitespace(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    ExtractedField capped(ExtractedField field, int maxLength) {
        if (field.value().length() <= maxLength) {
            return field;
        }
        return new ExtractedField(field.fieldKey(), field.value().substring(0, maxLength),
                field.confidence(), field.snippet(), field.origin());
    }
}
