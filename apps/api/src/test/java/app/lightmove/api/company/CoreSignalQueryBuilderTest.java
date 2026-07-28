package app.lightmove.api.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.company.service.CoreSignalQueryBuilder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The exact ES DSL JSON a criteria produces — pure translation, asserted without any HTTP. The two
 * credit guards (anchor required, GCC default geography) are the load-bearing cases.
 */
class CoreSignalQueryBuilderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CoreSignalQueryBuilder builder = new CoreSignalQueryBuilder(json);

    @Test
    @DisplayName("an anchorless criteria is refused outright — never a worldwide search")
    void anchorlessCriteriaRefused() {
        CoreSignalSearchCriteria criteria = new CoreSignalSearchCriteria(
                List.of(), List.of(), List.of("AE"), List.of(EmployeeBand.B_1_10), List.of());

        assertThatThrownBy(() -> builder.searchBody(criteria))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a full criteria renders industries, tags, countries and both band axes as bool clauses")
    void fullCriteriaRendersAllClauses() {
        CoreSignalSearchCriteria criteria = new CoreSignalSearchCriteria(
                List.of("Retail", "Wholesale"), List.of("grocery"), List.of("AE", "SA"),
                List.of(EmployeeBand.B_51_200), List.of(RevenueBand.R_5M_25M));

        JsonNode body = json.readTree(builder.searchBody(criteria));
        JsonNode must = body.path("query").path("bool").path("must");
        assertThat(must.size()).isEqualTo(4);

        JsonNode anchorShould = must.get(0).path("bool").path("should");
        assertThat(anchorShould.get(0).path("terms").path("industry").toString())
                .contains("Retail").contains("Wholesale");
        assertThat(anchorShould.get(1).path("terms").path("categories_and_keywords").toString())
                .contains("grocery");
        assertThat(must.get(0).path("bool").path("minimum_should_match").intValue()).isEqualTo(1);

        assertThat(must.get(1).path("terms").path("hq_country_iso2").toString())
                .contains("AE").contains("SA");

        JsonNode employeeRange = must.get(2).path("bool").path("should").get(0)
                .path("range").path("employees_count");
        assertThat(employeeRange.path("gte").intValue()).isEqualTo(51);
        assertThat(employeeRange.path("lte").intValue()).isEqualTo(200);

        JsonNode revenueRange = must.get(3).path("bool").path("should").get(0)
                .path("range").path(CoreSignalQueryBuilder.REVENUE_FIELD);
        assertThat(revenueRange.path("gte").longValue()).isEqualTo(5_000_000L);
        assertThat(revenueRange.path("lt").longValue()).isEqualTo(25_000_000L);
    }

    @Test
    @DisplayName("no market selected pins the search to the full GCC set, never the world")
    void noMarketDefaultsToGcc() {
        CoreSignalSearchCriteria criteria = new CoreSignalSearchCriteria(
                List.of("Retail"), List.of(), List.of(), List.of(), List.of());

        JsonNode body = json.readTree(builder.searchBody(criteria));
        JsonNode countries = body.path("query").path("bool").path("must").get(1)
                .path("terms").path("hq_country_iso2");
        assertThat(countries.size()).isEqualTo(6);
        assertThat(countries.toString())
                .contains("AE").contains("SA").contains("KW").contains("QA").contains("BH").contains("OM");
    }

    @Test
    @DisplayName("empty band axes produce no range clause at all")
    void emptyBandAxesOmitted() {
        CoreSignalSearchCriteria criteria = new CoreSignalSearchCriteria(
                List.of("Retail"), List.of(), List.of("AE"), List.of(), List.of());

        JsonNode must = json.readTree(builder.searchBody(criteria)).path("query").path("bool").path("must");
        assertThat(must.size()).isEqualTo(2); // anchor + countries only
    }

    @Test
    @DisplayName("open-ended bands carry only their bounded side")
    void openEndedBandsHalfBounded() {
        CoreSignalSearchCriteria criteria = new CoreSignalSearchCriteria(
                List.of("Retail"), List.of(), List.of("AE"),
                List.of(EmployeeBand.B_10000_PLUS), List.of(RevenueBand.R_UNDER_5M));

        JsonNode must = json.readTree(builder.searchBody(criteria)).path("query").path("bool").path("must");
        JsonNode employees = must.get(2).path("bool").path("should").get(0).path("range").path("employees_count");
        assertThat(employees.path("gte").intValue()).isEqualTo(10001);
        assertThat(employees.has("lte")).isFalse();

        JsonNode revenue = must.get(3).path("bool").path("should").get(0)
                .path("range").path(CoreSignalQueryBuilder.REVENUE_FIELD);
        assertThat(revenue.has("gte")).isFalse();
        assertThat(revenue.path("lt").longValue()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("the sort is revenue-desc with missing values last, id as tiebreak")
    void sortsRevenueDescMissingLast() {
        CoreSignalSearchCriteria criteria = new CoreSignalSearchCriteria(
                List.of("Retail"), List.of(), List.of(), List.of(), List.of());

        JsonNode sort = json.readTree(builder.searchBody(criteria)).path("sort");
        JsonNode revenue = sort.get(0).path(CoreSignalQueryBuilder.REVENUE_FIELD);
        assertThat(revenue.path("order").stringValue()).isEqualTo("desc");
        assertThat(revenue.path("missing").stringValue()).isEqualTo("_last");
        assertThat(sort.get(1).path("id").stringValue()).isEqualTo("asc");
    }

    @Test
    @DisplayName("the criteria hash input is order-insensitive but selection-sensitive")
    void canonicalStringStableUnderReordering() {
        CoreSignalSearchCriteria one = new CoreSignalSearchCriteria(
                List.of("Retail", "Wholesale"), List.of("a", "b"), List.of("AE", "SA"),
                List.of(EmployeeBand.B_1_10, EmployeeBand.B_11_50), List.of());
        CoreSignalSearchCriteria reordered = new CoreSignalSearchCriteria(
                List.of("Wholesale", "Retail"), List.of("b", "a"), List.of("SA", "AE"),
                List.of(EmployeeBand.B_11_50, EmployeeBand.B_1_10), List.of());
        CoreSignalSearchCriteria different = new CoreSignalSearchCriteria(
                List.of("Retail"), List.of("a", "b"), List.of("AE", "SA"),
                List.of(EmployeeBand.B_1_10, EmployeeBand.B_11_50), List.of());

        assertThat(one.canonicalString()).isEqualTo(reordered.canonicalString());
        assertThat(one.canonicalString()).isNotEqualTo(different.canonicalString());
    }
}
