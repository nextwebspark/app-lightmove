package app.lightmove.api.strategy.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Go-to-market segments (B2B, SaaS, Fintech…) mapped onto the universe's {@code keywords} aliases,
 * since the universe has no segment column and spells one segment several ways. Aliases must be
 * lower-case so the overlap test keeps the {@code idx_lm_apollo_kw} GIN index. A filter stores the
 * segment name, not its aliases.
 */
@Component
public class MarketSegments {

    private static final String RESOURCE = "data/market-segments.json";

    private final Map<String, List<String>> keywordsBySegment;

    public MarketSegments(ObjectMapper json) {
        this.keywordsBySegment = ClasspathVocabulary.read(json, RESOURCE);
    }

    /** In file order, which is the order the accordion renders. */
    public Map<String, List<String>> segments() {
        return keywordsBySegment;
    }

    public List<String> keywordsOf(String segment) {
        return keywordsBySegment.getOrDefault(segment, List.of());
    }

    /** An unknown name contributes nothing: a client with a stale list loses a chip, not the request. */
    public List<String> keywordsOfAll(List<String> segments) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String segment : segments) {
            keywords.addAll(keywordsOf(segment));
        }
        return List.copyOf(keywords);
    }
}
