package app.lightmove.api.enrichment.sourcing.constant;

import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.C_LEVEL;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.MANAGER;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.SENIOR_VP;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.SPECIALIST;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.VP_HEAD_DIRECTOR;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Who a search firm maps for a role, as researchers mapped it on live mandates: the seat and the bench under it. */
class SearchLevelTest {

    @Test
    @DisplayName("a C-suite role maps down to Head and Director, N-1 to managers, N-2 and N-3 from Director to specialist")
    void eachSeniorityMapsItsBench() {
        assertThat(SearchLevel.rangeFor(Seniority.BOARD)).containsExactly(C_LEVEL);
        assertThat(SearchLevel.rangeFor(Seniority.C_SUITE)).containsExactly(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR);
        assertThat(SearchLevel.rangeFor(null)).isEqualTo(SearchLevel.rangeFor(Seniority.C_SUITE));
        assertThat(SearchLevel.rangeFor(Seniority.N_MINUS_1))
                .containsExactly(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR, MANAGER);
        assertThat(SearchLevel.rangeFor(Seniority.N_MINUS_3)).containsExactly(VP_HEAD_DIRECTOR, MANAGER, SPECIALIST);
    }

    @Test
    @DisplayName("the first purchase reaches Head and Director, where most of a function's bench sits, and managers for a junior role")
    void theFirstTierFloor() {
        assertThat(SearchLevel.firstTierFloor(Seniority.C_SUITE)).isEqualTo(VP_HEAD_DIRECTOR);
        assertThat(SearchLevel.firstTierFloor(Seniority.N_MINUS_1)).isEqualTo(VP_HEAD_DIRECTOR);
        assertThat(SearchLevel.firstTierFloor(null)).isEqualTo(VP_HEAD_DIRECTOR);
        assertThat(SearchLevel.firstTierFloor(Seniority.N_MINUS_2)).isEqualTo(MANAGER);
    }
}
