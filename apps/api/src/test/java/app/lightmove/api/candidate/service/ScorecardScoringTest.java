package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.dto.CriterionScoreRequest;
import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.candidate.model.ScorecardCriterion;
import app.lightmove.api.candidate.model.ScorecardTally;
import app.lightmove.api.common.constant.CompetencyPanel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The assessment's arithmetic, and what a save keeps — the cases the SPA's mirror is tested against too. */
class ScorecardScoringTest {

    private static final CompetencyPanel TECHNICAL = CompetencyPanel.TECHNICAL;
    private static final CompetencyPanel BEHAVIOURAL = CompetencyPanel.BEHAVIOURAL;

    private static final List<ScorecardCriterion> BRIEF = List.of(
            new ScorecardCriterion(TECHNICAL, "Enterprise Architecture", "ERP at group scale", 35),
            new ScorecardCriterion(TECHNICAL, "Cybersecurity", null, 35),
            new ScorecardCriterion(TECHNICAL, "IT Operations", null, 30),
            new ScorecardCriterion(BEHAVIOURAL, "Strategic Leadership", null, 30),
            new ScorecardCriterion(BEHAVIOURAL, "Stakeholder Influence", null, 25),
            new ScorecardCriterion(BEHAVIOURAL, "Change Management", null, 25),
            new ScorecardCriterion(BEHAVIOURAL, "Resilience", null, 20));

    @Test
    @DisplayName("the mockup's example adds up out of 100, each panel carrying its share")
    void tallyMatchesTheMockup() {
        ScorecardTally tally = ScorecardScoring.tally(BRIEF, 60, List.of(
                score(TECHNICAL, "Enterprise Architecture", 8), score(TECHNICAL, "Cybersecurity", 6),
                score(TECHNICAL, "IT Operations", 7), score(BEHAVIOURAL, "Strategic Leadership", 8),
                score(BEHAVIOURAL, "Stakeholder Influence", 6), score(BEHAVIOURAL, "Change Management", 7),
                score(BEHAVIOURAL, "Resilience", 7)));

        assertThat(tally.panels().get(0).share()).isEqualTo(60);
        assertThat(tally.panels().get(0).points()).isEqualTo(42.0);
        assertThat(tally.panels().get(1).points()).isEqualTo(28.2);
        assertThat(tally.overall()).isEqualTo(70.2);
        assertThat(tally.scoredCount()).isEqualTo(7);
        assertThat(tally.panels().get(0).criteria().get(0).maxPoints()).isEqualTo(21.0);
    }

    @Test
    @DisplayName("an unscored criterion earns nothing and is counted as unscored, never as a zero")
    void unscoredIsAnOpenQuestion() {
        ScorecardTally tally = ScorecardScoring.tally(BRIEF, 60, List.of(score(TECHNICAL, "Cybersecurity", 0)));

        assertThat(tally.scoredCount()).isEqualTo(1);
        assertThat(tally.overall()).isZero();
        assertThat(tally.panels().get(0).average()).isEqualTo(0.0);
        assertThat(tally.panels().get(1).average()).isNull();
        assertThat(tally.panels().get(0).criteria().get(0).score()).isNull();
    }

    @Test
    @DisplayName("weights that do not add up to 100 are read as parts of their panel")
    void weightsAreNormalisedWithinAPanel() {
        List<ScorecardCriterion> brief = List.of(
                new ScorecardCriterion(TECHNICAL, "One", null, 10),
                new ScorecardCriterion(TECHNICAL, "Two", null, 30),
                new ScorecardCriterion(BEHAVIOURAL, "Three", null, 0),
                new ScorecardCriterion(BEHAVIOURAL, "Four", null, 0));

        ScorecardTally tally = ScorecardScoring.tally(brief, 50, List.of(score(TECHNICAL, "Two", 10)));

        assertThat(tally.panels().get(0).criteria().get(1).weightInPanel()).isEqualTo(75.0);
        assertThat(tally.panels().get(1).criteria().get(0).weightInPanel()).isEqualTo(50.0);
        assertThat(tally.overall()).isEqualTo(37.5);
    }

    @Test
    @DisplayName("a brief scoring one panel still reads out of 100")
    void anEmptyPanelGivesItsShareAway() {
        List<ScorecardCriterion> brief = List.of(new ScorecardCriterion(BEHAVIOURAL, "Only", null, 100));

        ScorecardTally tally = ScorecardScoring.tally(brief, 70, List.of(score(BEHAVIOURAL, "Only", 10)));

        assertThat(tally.panels().get(0).share()).isZero();
        assertThat(tally.panels().get(1).share()).isEqualTo(100);
        assertThat(tally.overall()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("a score matches its criterion whatever the case, and one the brief dropped is retired, not counted")
    void scoresMatchOnPanelAndName() {
        ScorecardTally tally = ScorecardScoring.tally(BRIEF, 60, List.of(
                score(TECHNICAL, "enterprise architecture ", 10),
                score(TECHNICAL, "Data Strategy", 9),
                score(BEHAVIOURAL, "Cybersecurity", 4)));

        assertThat(tally.panels().get(0).criteria().get(0).score()).isEqualTo(10);
        assertThat(tally.panels().get(0).criteria().get(1).score()).isNull();
        assertThat(tally.retired()).extracting(CriterionScore::name).containsExactly("Data Strategy", "Cybersecurity");
        assertThat(tally.overall()).isEqualTo(21.0);
    }

    @Test
    @DisplayName("a save keeps the brief's spelling and weight, held scores for dropped criteria, and nothing empty")
    void mergeKeepsWhatASaveCannotSee() {
        List<CriterionScore> held = List.of(
                new CriterionScore(TECHNICAL, "Data Strategy", 20, 9, "Ran the group's data office"),
                new CriterionScore(TECHNICAL, "Cybersecurity", 35, 2, null));
        List<CriterionScoreRequest> sent = List.of(
                new CriterionScoreRequest("technical", "cybersecurity", 6, "  "),
                new CriterionScoreRequest("behavioural", "Resilience", null, null),
                new CriterionScoreRequest("technical", "Removed while the tab was open", 5, null));

        List<CriterionScore> merged = CandidateScorecardService.merged(held, sent, BRIEF);

        assertThat(merged).containsExactly(
                new CriterionScore(TECHNICAL, "Cybersecurity", 35, 6, null),
                new CriterionScore(TECHNICAL, "Data Strategy", 20, 9, "Ran the group's data office"));
    }

    private static CriterionScore score(CompetencyPanel panel, String name, int score) {
        return new CriterionScore(panel, name, 0, score, null);
    }
}
