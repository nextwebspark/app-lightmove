package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateCompensationTest {

    private static final List<AllowanceLine> LINES =
            List.of(new AllowanceLine("Housing", 414_000L), new AllowanceLine("Transport", 138_000L));

    @Test
    @DisplayName("lines with no stated total supply it")
    void linesSupplyAMissingTotal() {
        CandidateCompensation compensation = packageWith(null, LINES);

        assertThat(compensation.allowances()).isEqualTo(552_000L);
        assertThat(compensation.breakdown().allowanceLines()).hasSize(2);
    }

    @Test
    @DisplayName("a total that agrees with its lines keeps them")
    void anAgreeingTotalKeepsItsLines() {
        assertThat(packageWith(552_000L, LINES).breakdown().allowanceLines()).hasSize(2);
    }

    @Test
    @DisplayName("a total that contradicts its lines wins and drops them")
    void aContradictingTotalDropsTheLines() {
        CandidateCompensation compensation = packageWith(600_000L, LINES);

        assertThat(compensation.allowances()).isEqualTo(600_000L);
        assertThat(compensation.breakdown().allowanceLines()).isEmpty();
    }

    @Test
    @DisplayName("blank lines are dropped, and None never stands beside a real instrument")
    void breakdownIsTidied() {
        CompensationBreakdown breakdown = new CompensationBreakdown(
                List.of(new AllowanceLine(" ", null), new AllowanceLine("Education", 1L)),
                List.of(LongTermIncentiveType.NONE, LongTermIncentiveType.CASH, LongTermIncentiveType.CASH));

        assertThat(breakdown.allowanceLines()).extracting(AllowanceLine::label).containsExactly("Education");
        assertThat(breakdown.longTermIncentiveTypes()).containsExactly(LongTermIncentiveType.CASH);
    }

    private static CandidateCompensation packageWith(Long allowances, List<AllowanceLine> lines) {
        return new CandidateCompensation("AED", 1_800_000L, null, allowances, null, null,
                new CompensationBreakdown(lines, List.of()));
    }
}
