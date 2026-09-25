package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a disclosed package comes to, shared by the chapter that ranks it and the one that maps it. */
class PackagesTest {

    @Test
    @DisplayName("fixed is base plus allowances; the package adds bonus and long-term incentive")
    void packageIsEveryElement() {
        CandidateCompensationDto full = compensation("USD", 300_000L, 50_000L, 20_000L, 100_000L);

        assertThat(Packages.fixedOf(full)).isEqualTo(320_000L);
        assertThat(Packages.totalOf(full)).isEqualTo(470_000L);
    }

    @Test
    @DisplayName("a missing element is nothing, not a refusal — research arrives in pieces")
    void missingElementsCountAsNothing() {
        CandidateCompensationDto baseOnly = compensation("USD", 300_000L, null, null, null);

        assertThat(Packages.fixedOf(baseOnly)).isEqualTo(300_000L);
        assertThat(Packages.totalOf(baseOnly)).isEqualTo(300_000L);
        assertThat(Packages.isDisclosed(baseOnly)).isTrue();
    }

    @Test
    @DisplayName("a package is disclosed once a base salary is on file, and not before")
    void disclosureTurnsOnTheBaseSalary() {
        assertThat(Packages.isDisclosed(compensation("USD", null, 50_000L, null, null))).isFalse();
        assertThat(Packages.isDisclosed(null)).isFalse();
    }

    @Test
    @DisplayName("a package with no currency is read as the brief's; another currency is not")
    void currencyDefaultsToTheBriefs() {
        assertThat(Packages.isInCurrency(compensation(null, 300_000L, null, null, null), "USD")).isTrue();
        assertThat(Packages.isInCurrency(compensation("usd", 300_000L, null, null, null), "USD")).isTrue();
        assertThat(Packages.isInCurrency(compensation("AED", 300_000L, null, null, null), "USD")).isFalse();
    }

    @Test
    @DisplayName("the median is the middle of a sorted run, and the mean of two middles")
    void medianTakesTheMiddle() {
        assertThat(Packages.medianOf(List.of(300L, 100L, 200L))).isEqualTo(200L);
        assertThat(Packages.medianOf(List.of(400L, 100L, 200L, 300L))).isEqualTo(250L);
        assertThat(Packages.medianOf(List.of(500L))).isEqualTo(500L);
    }

    @Test
    @DisplayName("nothing to rank has no median, rather than a zero somebody would read as a figure")
    void anEmptyRunHasNoMedian() {
        assertThat(Packages.medianOf(List.of())).isNull();
    }

    private static CandidateCompensationDto compensation(String currency, Long base, Long bonus,
                                                         Long allowances, Long longTermIncentive) {
        return new CandidateCompensationDto(currency, base, bonus, allowances, longTermIncentive, null, null, null);
    }
}
