package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The company tables name their sort with these wire tokens, and the frontend mirrors the same list in
 * its own {@code CompanySortField} union ({@code features/strategy/api/types.ts}). A rename on either
 * side is a 400 the table renders as an empty list, so both sides pin the set.
 */
class CompanySortFieldTest {

    private static final String[] WIRE_TOKENS = {
            "name", "sector", "country", "location", "employees", "revenue", "founded"};

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
                // Apollo publishes a revenue figure on one row in ten, so an ascending revenue sort
                // without NULLS LAST is nine pages of nothing.
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
        assertThat(CompanySortField.fromValue("company_name; DROP TABLE app_lm_apollo_companies")).isNull();
        assertThat(CompanySortField.fromValue("NAME")).isNull();
        assertThat(CompanySortField.fromValue("")).isNull();
    }
}
