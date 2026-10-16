package app.uncava.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.uncava.api.common.constant.CompetencyPanel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scores column round-trips through Hibernate's JSON mapper — Jackson 2, not the app's Jackson 3. A helper
 * named {@code isEmpty()} once shipped on this record: the mapper wrote it as an "empty" property, then refused
 * to read it back when Hibernate copied the column, so the first save with a score answered 500.
 */
class CriterionScoreJsonTest {

    @Test
    @DisplayName("a stored score is its five components and nothing else, and reads back as written")
    void roundTripsThroughHibernatesMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<CriterionScore> scores = List.of(
                new CriterionScore(CompetencyPanel.TECHNICAL, "Treasury", 60, 8, "Ran group treasury"),
                new CriterionScore(CompetencyPanel.BEHAVIOURAL, "Leadership", 100, null, null));

        String json = mapper.writeValueAsString(scores);

        List<String> stored = new java.util.ArrayList<>();
        mapper.readTree(json).get(0).fieldNames().forEachRemaining(stored::add);
        assertThat(stored).containsExactlyInAnyOrder("panel", "name", "weight", "score", "evidence");
        assertThat(mapper.readValue(json, new TypeReference<List<CriterionScore>>() {})).isEqualTo(scores);
    }
}
