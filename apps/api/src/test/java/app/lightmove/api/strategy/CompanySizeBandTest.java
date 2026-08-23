package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.RevenueBand;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The size bands turn a chip into a numeric range, and the ranges are the whole contract: the same
 * bounds count the chip and filter on it, so a gap or an overlap would be a company the sidebar
 * counts in one band and lists in another — or in none.
 *
 * <p>The wire tokens are pinned here for the same reason {@code CompanySortFieldTest} pins its own:
 * they are what a saved filter stores, so a rename silently empties every search saved before it.
 */
class CompanySizeBandTest {

    @Test
    @DisplayName("the employee bands tile the range without gaps or overlaps")
    void employeeBandsTileTheRange() {
        EmployeeBand[] bands = EmployeeBand.values();
        assertThat(bands[0].lowerBound()).isEqualTo(1);
        assertThat(bands[bands.length - 1].upperBound())
                .as("the top band must run to infinity")
                .isNull();

        for (int index = 0; index < bands.length - 1; index++) {
            assertThat(bands[index].upperBound())
                    .as("%s must be closed above", bands[index])
                    .isNotNull();
            assertThat(bands[index + 1].lowerBound())
                    .as("%s must start where %s ends", bands[index + 1], bands[index])
                    .isEqualTo(bands[index].upperBound() + 1);
        }
    }

    @Test
    @DisplayName("the revenue bands tile the range, with Unknown standing outside it")
    void revenueBandsTileTheRange() {
        RevenueBand[] numeric = Arrays.stream(RevenueBand.values())
                .filter(band -> !band.isUnknown())
                .toArray(RevenueBand[]::new);

        assertThat(numeric[0].lowerBound()).isZero();
        assertThat(numeric[numeric.length - 1].upperBound()).isNull();

        for (int index = 0; index < numeric.length - 1; index++) {
            assertThat(numeric[index + 1].lowerBound())
                    .as("%s must start where %s ends", numeric[index + 1], numeric[index])
                    .isEqualTo(numeric[index].upperBound() + 1);
        }
    }

    @Test
    @DisplayName("Unknown carries no bounds, so a caller reading them must check isUnknown first")
    void unknownCarriesNoBounds() {
        assertThat(RevenueBand.R_UNKNOWN.isUnknown()).isTrue();
        assertThat(RevenueBand.R_UNKNOWN.lowerBound()).isNull();
        assertThat(RevenueBand.R_UNKNOWN.upperBound()).isNull();
        assertThat(Arrays.stream(RevenueBand.values()).filter(RevenueBand::isUnknown)).hasSize(1);
    }

    @Test
    @DisplayName("the wire tokens are slugs a relabelling cannot break")
    void wireTokensAreStable() {
        assertThat(Arrays.stream(EmployeeBand.values()).map(EmployeeBand::value))
                .containsExactly("1-10", "11-20", "21-50", "51-100", "101-200", "201-500", "501-1000",
                        "1001-2000", "2001-5000", "5001-10000", "10000-plus");
        assertThat(Arrays.stream(RevenueBand.values()).map(RevenueBand::value))
                .containsExactly("under-1m", "1m-10m", "10m-50m", "50m-100m", "100m-200m", "200m-500m",
                        "500m-1b", "1b-5b", "5b-10b", "10b-plus", "unknown");
    }

    @Test
    @DisplayName("an unknown token resolves to null rather than silently widening the filter")
    void unknownTokensDoNotResolve() {
        assertThat(EmployeeBand.fromValue("1-10 OR 1=1")).isNull();
        assertThat(EmployeeBand.fromValue("B_1_10")).as("the enum name is not the wire token").isNull();
        assertThat(RevenueBand.fromValue("$10B+")).as("the label is not the wire token").isNull();
    }
}
