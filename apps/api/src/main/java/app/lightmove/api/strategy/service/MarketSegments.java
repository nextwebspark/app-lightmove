package app.lightmove.api.strategy.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * How a company goes to market — B2B, SaaS, Fintech, Retail and the rest — mapped onto the keywords
 * that actually express it in the universe.
 *
 * <p>{@code app_lm_apollo_companies} has no market-segment column; what it has is {@code keywords},
 * a free-text array populated on 67,306 of 71,822 rows and carrying exactly these tokens. So a
 * segment is a small set of keyword aliases, and the filter is an array-overlap test. The aliases
 * are the point: the universe spells the same segment two ways ({@code e-commerce} on 10,670 rows
 * and {@code ecommerce} on 778; {@code non-profit} on 2,548 and {@code nonprofit} on 21), and a
 * one-token-per-segment enum would quietly lose those rows.
 *
 * <p><b>Keywords are lower-case throughout the table</b> — zero rows carry a mixed-case one — which is
 * why the match can run as a plain {@code keywords && ARRAY[…]} and use the existing
 * {@code idx_lm_apollo_kw} GIN index rather than lower-casing every element per row and losing it.
 * The aliases below must therefore be written lower-case; {@code MarketSegmentsTest} asserts that.
 *
 * <p><b>The segment name is what a filter stores</b>, not its aliases — the opposite of
 * {@link SectorTaxonomy}, and deliberately. A sector group is a shorthand for industries the user can
 * also pick individually, so storing the group would let a re-tuned taxonomy silently widen a saved
 * mandate. A segment has no sub-chips: it <i>is</i> the selection, and adding an alias to it later
 * should improve an existing saved search rather than leave it matching the old spelling only.
 */
@Component
public class MarketSegments {

    private static final String RESOURCE = "data/market-segments.json";

    private final Map<String, List<String>> keywordsBySegment;

    public MarketSegments(ObjectMapper json) {
        this.keywordsBySegment = load(json);
    }

    private static Map<String, List<String>> load(ObjectMapper json) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return json.readValue(in, new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + RESOURCE, e);
        }
    }

    /** Every segment in file order, which is the order the accordion renders. */
    public Map<String, List<String>> segments() {
        return keywordsBySegment;
    }

    /** The keyword aliases for one segment, or empty if the name is not one we carry. */
    public List<String> keywordsOf(String segment) {
        return keywordsBySegment.getOrDefault(segment, List.of());
    }

    /**
     * The keywords covering all the named segments, de-duplicated. An unknown name contributes
     * nothing rather than throwing: a client holding a stale list should lose a chip, not the request.
     */
    public List<String> keywordsOfAll(List<String> segments) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String segment : segments) {
            keywords.addAll(keywordsOf(segment));
        }
        return List.copyOf(keywords);
    }
}
