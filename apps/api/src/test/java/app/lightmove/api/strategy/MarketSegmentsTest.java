package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.service.MarketSegments;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Market segments are keyword aliases, and the aliases are the fragile part: they have to match the
 * universe's own spelling exactly, because the filter is an index-backed array overlap rather than a
 * fuzzy match.
 */
class MarketSegmentsTest {

    private final MarketSegments segments = new MarketSegments(new ObjectMapper());

    @Test
    @DisplayName("every keyword is lower-case, so the overlap can use the GIN index")
    void keywordsAreLowerCase() {
        for (Map.Entry<String, List<String>> segment : segments.segments().entrySet()) {
            for (String keyword : segment.getValue()) {
                // Every keyword in app_lm_apollo_companies is lower-case, which is what lets the
                // filter run as `keywords && ARRAY[…]` against idx_lm_apollo_kw. A mixed-case alias
                // here would simply match nothing.
                assertThat(keyword)
                        .as("'%s' under segment '%s'", keyword, segment.getKey())
                        .isEqualTo(keyword.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    @DisplayName("every segment carries at least one keyword")
    void noEmptySegments() {
        for (Map.Entry<String, List<String>> segment : segments.segments().entrySet()) {
            assertThat(segment.getValue())
                    .as("segment '%s' must not be empty", segment.getKey())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("the spelling variants the universe actually carries are all covered")
    void spellingVariantsAreCovered() {
        // Both spellings are present in the table — e-commerce on 10,670 rows and ecommerce on 778,
        // non-profit on 2,548 and nonprofit on 21. A single-token segment would drop the smaller half.
        assertThat(segments.keywordsOf("E-commerce")).contains("e-commerce", "ecommerce");
        assertThat(segments.keywordsOf("Non-Profit")).contains("non-profit", "nonprofit");
    }

    @Test
    @DisplayName("selecting several segments merges their keywords without duplicates")
    void mergesWithoutDuplicates() {
        List<String> keywords = segments.keywordsOfAll(List.of("B2B", "SaaS", "E-commerce"));

        assertThat(keywords).containsExactlyInAnyOrder("b2b", "saas", "e-commerce", "ecommerce");
        assertThat(keywords).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("an unknown segment contributes nothing rather than failing the request")
    void unknownSegmentIsIgnored() {
        assertThat(segments.keywordsOf("B2Everything")).isEmpty();
        assertThat(segments.keywordsOfAll(List.of("B2Everything"))).isEmpty();
    }

    @Test
    @DisplayName("the segments are the eleven the screen offers, in the order it renders them")
    void segmentsAreTheScreensOwnList() {
        assertThat(segments.segments().keySet())
                .containsExactly("B2B", "B2C", "B2B2C", "E-commerce", "Fintech", "D2C", "Non-Profit",
                        "SaaS", "Consulting", "Services", "Retail");
    }
}
