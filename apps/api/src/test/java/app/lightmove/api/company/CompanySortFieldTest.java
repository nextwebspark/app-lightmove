package app.lightmove.api.company;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.company.constant.CompanySortField;
import app.lightmove.api.company.constant.SortDirection;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Sourcing table names its sort with these wire tokens, and the frontend keeps a mirror of the
 * same list ({@code features/sourcing/lib/sortFields.test.ts}). A rename on either side is a 400 the
 * table renders as an empty list, so both sides pin the set.
 */
class CompanySortFieldTest {

    private static final String[] WIRE_TOKENS = {
            "name", "tier", "sector", "employees", "revenue", "location", "founded", "country"};

    @Test
    @DisplayName("the wire tokens are exactly the set the frontend mirror sends")
    void wireTokensMatchTheFrontendMirror() {
        assertThat(Arrays.stream(CompanySortField.values()).map(CompanySortField::value))
                .containsExactlyInAnyOrder(WIRE_TOKENS);
    }

    @Test
    @DisplayName("every field emits an ordering that buries unknowns in both directions")
    void everyFieldBuriesUnknownsBothWays() {
        for (CompanySortField field : CompanySortField.values()) {
            for (SortDirection direction : SortDirection.values()) {
                // An ascending sort that opens on a page of blanks is not what the header asked for.
                assertThat(field.orderByTerms(direction))
                        .as("%s %s", field, direction)
                        .contains(direction.sqlKeyword())
                        .contains("NULLS LAST");
            }
        }
    }

    @Test
    @DisplayName("an unknown token resolves to null rather than reaching SQL")
    void unknownTokensDoNotResolve() {
        assertThat(CompanySortField.fromValue("name; DROP TABLE app_lm_companies")).isNull();
        assertThat(CompanySortField.fromValue("NAME")).isNull();
        assertThat(CompanySortField.fromValue("")).isNull();
    }
}
