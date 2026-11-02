package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The dataset matches {@code includes} as a substring, so what it is asked leaves out the words hiding inside others. */
class BrightDataPeopleSearchTest {

    @Test
    @DisplayName("CTO is never sent to the dataset, where it would buy every Doctor and Director at the company")
    void aHiddenWordIsNeverSent() {
        SourcingSpec technology = SourcingSpec.of(List.of("CTO", "Chief", "Director", "VP"),
                List.of("CTO", "Technology", "Engineering", "Information"), List.of("Assistant"), null);

        Map<String, Object> body = BrightDataPeopleSearch.searchBody("a-pharmacy-chain", technology, List.of("AE"),
                List.of(), 10).orElseThrow();

        String sent = body.toString();
        assertThat(sent).doesNotContain("CTO").contains("Chief", "Director", "VP", "Technology", "Engineering",
                "Information", "Assistant", "a-pharmacy-chain");
    }

    @Test
    @DisplayName("a title group holding nothing but such words is not searched at all, rather than searched by the other group alone")
    void aGroupOfHiddenWordsIsNotSearched() {
        SourcingSpec operations = SourcingSpec.of(List.of("Chief", "Head"), List.of("COO"), List.of(), null);

        assertThat(BrightDataPeopleSearch.searchBody("dp-world", operations, List.of(), List.of(), 10)).isEmpty();
    }
}
