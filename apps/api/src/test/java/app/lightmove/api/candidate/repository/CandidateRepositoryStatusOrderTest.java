package app.lightmove.api.candidate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CandidateRepository#findTriageCompanyIdsRankedByExecutiveStatus} and its
 * {@code ...AndExecutiveStatuses} sibling rank rows with a native {@code CASE} whose branches are
 * {@link CandidateStatus}'s enum names spelled out as string literals — {@code triagecompany} may not
 * import {@code candidate}'s enum, and Spring Data's {@code @Query} needs a compile-time constant, so
 * the {@code CASE} can't reference it directly. A rename or reorder of the enum has nothing else to
 * catch that drift: an unmatched name falls into the {@code CASE}'s {@code else null} and silently
 * sorts last instead of failing loud. This test pins the mirrored list so that drift is a red build
 * here instead. (The same literal spelling is also mirrored in
 * {@code TriageCompanyReadService.EXECUTIVE_STATUS_TOKENS} and
 * {@code MappedExecutiveLookupAdapter#triageCompanyIdsWithExecutiveStatusIn}; a rename should grep for
 * all three.)
 */
class CandidateRepositoryStatusOrderTest {

    private static final List<String> CASE_LITERAL_ORDER = List.of(
            "IDENTIFIED", "CONTACTED", "ENGAGED", "INTERESTED", "NOT_INTERESTED", "OFF_LIMITS", "OUT_OF_SCOPE");

    @Test
    @DisplayName("the ranking CASE's literal order names every CandidateStatus, once each")
    void caseLiteralsMatchTheEnum() {
        assertThat(CASE_LITERAL_ORDER)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(CandidateStatus.values()).map(Enum::name).toList());
    }
}
