package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.candidate.model.ScorecardCriterion;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.report.model.BriefWeighting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a ranking scenario may hold against the brief, and how a saved one reads once the brief has moved. */
class RankingScenarioRulesTest {

    private static final ScorecardCriterion ERP = technical("ERP", 30);
    private static final ScorecardCriterion SECURITY = technical("Security", 30);
    private static final ScorecardCriterion LEADERSHIP = behavioural("Leadership", 60);
    private static final ScorecardCriterion INFLUENCE = behavioural("Influence", 40);
    private static final List<ScorecardCriterion> BRIEF = List.of(ERP, SECURITY, LEADERSHIP, INFLUENCE);

    @Test
    @DisplayName("a group left as the brief has it passes, though its weights do not total 100")
    void untouchedGroupPassesOffHundred() {
        Map<String, Integer> weights = weights(ERP, 30, SECURITY, 30, LEADERSHIP, 50, INFLUENCE, 50);

        assertThatCode(() -> RankingScenarioRules.refuseUnfit(weights, BRIEF)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a group someone changed must total 100")
    void changedGroupMustTotalHundred() {
        Map<String, Integer> weights = weights(ERP, 40, SECURITY, 30, LEADERSHIP, 60, INFLUENCE, 40);

        assertThatThrownBy(() -> RankingScenarioRules.refuseUnfit(weights, BRIEF))
                .isInstanceOfSatisfying(ApiException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("weights naming another brief's competencies are refused as the brief having changed")
    void otherCompetenciesAreABriefChange() {
        Map<String, Integer> missingOne = weights(ERP, 50, SECURITY, 50, LEADERSHIP, 60);
        Map<String, Integer> unknown = new LinkedHashMap<>(weights(ERP, 30, SECURITY, 30, LEADERSHIP, 60, INFLUENCE, 40));
        unknown.put("TECHNICAL:cloud", 0);

        for (Map<String, Integer> weights : List.of(missingOne, unknown)) {
            assertThatThrownBy(() -> RankingScenarioRules.refuseUnfit(weights, BRIEF))
                    .isInstanceOfSatisfying(ApiException.class,
                            refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.RANKING_BRIEF_CHANGED));
        }
    }

    @Test
    @DisplayName("the brief's own weighting is the baseline; another share or weight is a scenario")
    void baselineIsTheBriefsOwnWeighting() {
        Map<String, Integer> briefs = RankingScenarioRules.briefWeightsOf(BRIEF);

        assertThat(RankingScenarioRules.isBaseline(60, briefs, BRIEF, 60)).isTrue();
        assertThat(RankingScenarioRules.isBaseline(70, briefs, BRIEF, 60)).isFalse();
        assertThat(RankingScenarioRules.isBaseline(60,
                weights(ERP, 50, SECURITY, 50, LEADERSHIP, 60, INFLUENCE, 40), BRIEF, 60)).isFalse();
    }

    @Test
    @DisplayName("after a brief edit, a new competency takes the brief's weight and a dropped one is named")
    void savedScenarioReadsOntoTheBriefAsItStands() {
        BriefWeighting savedAgainst = RankingScenarioRules.weightingOf(BRIEF, 60);
        Map<String, Integer> saved = weights(ERP, 70, SECURITY, 30, LEADERSHIP, 60, INFLUENCE, 40);
        ScorecardCriterion cloud = technical("Cloud", 20);
        List<ScorecardCriterion> edited = List.of(ERP, cloud, LEADERSHIP, INFLUENCE);

        assertThat(RankingScenarioRules.weightsOnto(saved, edited))
                .containsExactly(Map.entry(ERP.matchKey(), 70), Map.entry(cloud.matchKey(), 20),
                        Map.entry(LEADERSHIP.matchKey(), 60), Map.entry(INFLUENCE.matchKey(), 40));
        assertThat(RankingScenarioRules.droppedSince(savedAgainst, edited)).containsExactly("Security");
        assertThat(RankingScenarioRules.briefChangedSince(savedAgainst, edited, 60)).isTrue();
        assertThat(RankingScenarioRules.briefChangedSince(savedAgainst, BRIEF, 60)).isFalse();
        assertThat(RankingScenarioRules.briefChangedSince(savedAgainst, BRIEF, 50)).isTrue();
    }

    private static Map<String, Integer> weights(Object... criterionThenWeight) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (int index = 0; index < criterionThenWeight.length; index += 2) {
            weights.put(((ScorecardCriterion) criterionThenWeight[index]).matchKey(),
                    (Integer) criterionThenWeight[index + 1]);
        }
        return weights;
    }

    private static ScorecardCriterion technical(String name, int weight) {
        return new ScorecardCriterion(CompetencyPanel.TECHNICAL, name, null, weight);
    }

    private static ScorecardCriterion behavioural(String name, int weight) {
        return new ScorecardCriterion(CompetencyPanel.BEHAVIOURAL, name, null, weight);
    }
}
