package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.candidate.model.ScorecardCriterion;
import app.lightmove.api.candidate.model.ScorecardPanelTally;
import app.lightmove.api.candidate.model.ScorecardTally;
import app.lightmove.api.candidate.model.ScoredCriterion;
import app.lightmove.api.common.constant.CompetencyPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An assessment's arithmetic, out of 100: each panel carries its share of the brief ({@code technicalShare}, the
 * behavioural panel the rest), each criterion its weight's part of its panel, and a score of 10 earns all of it.
 * The SPA's {@code lib/scorecardScoring.ts} draws the same numbers while a stepper moves, so the two change
 * together.
 *
 * <p>Weights are normalised within a panel, because nothing makes a brief's add up to 100; a panel with no
 * criteria gives its share to the other, so a brief scoring one panel still reads out of 100. An unscored
 * criterion earns nothing and is counted as unscored — missing evidence is an open question, not a zero.
 */
final class ScorecardScoring {

    private ScorecardScoring() {}

    static ScorecardTally tally(List<ScorecardCriterion> brief, int technicalShare, List<CriterionScore> stored) {
        Map<String, CriterionScore> scoreByKey = stored.stream()
                .collect(Collectors.toMap(CriterionScore::matchKey, Function.identity(), (first, second) -> first));
        List<ScorecardCriterion> technical = ofPanel(brief, CompetencyPanel.TECHNICAL);
        List<ScorecardCriterion> behavioural = ofPanel(brief, CompetencyPanel.BEHAVIOURAL);
        int clampedShare = Math.clamp(technicalShare, 0, 100);
        int technicalPart = technical.isEmpty() ? 0 : behavioural.isEmpty() ? 100 : clampedShare;
        int behaviouralPart = behavioural.isEmpty() ? 0 : 100 - technicalPart;

        List<ScorecardPanelTally> panels = new ArrayList<>();
        double overall = 0;
        int scoredCount = 0;
        for (var panel : List.of(
                new PanelInput(CompetencyPanel.TECHNICAL, technical, technicalPart),
                new PanelInput(CompetencyPanel.BEHAVIOURAL, behavioural, behaviouralPart))) {
            ScorecardPanelTally tallied = tallyPanel(panel, scoreByKey);
            panels.add(tallied);
            overall += tallied.points();
            scoredCount += tallied.scoredCount();
        }

        Set<String> briefKeys = brief.stream().map(ScorecardCriterion::matchKey).collect(Collectors.toSet());
        List<CriterionScore> retired = stored.stream()
                .filter(score -> !briefKeys.contains(score.matchKey()))
                .filter(score -> !score.holdsNothing())
                .toList();
        return new ScorecardTally(roundOne(overall), scoredCount, brief.size(), panels, retired);
    }

    private static ScorecardPanelTally tallyPanel(PanelInput input, Map<String, CriterionScore> scoreByKey) {
        int weightSum = input.criteria().stream().mapToInt(ScorecardCriterion::weight).sum();
        int count = input.criteria().size();
        List<ScoredCriterion> criteria = new ArrayList<>();
        double points = 0;
        int scoreSum = 0;
        int scored = 0;
        for (ScorecardCriterion criterion : input.criteria()) {
            double weightInPanel = weightSum == 0 ? 100.0 / count : 100.0 * criterion.weight() / weightSum;
            double maxPoints = weightInPanel / 100 * input.share();
            CriterionScore given = scoreByKey.get(criterion.matchKey());
            Integer score = given == null ? null : given.score();
            double earned = score == null ? 0 : score / 10.0 * maxPoints;
            if (score != null) {
                scoreSum += score;
                scored++;
            }
            points += earned;
            criteria.add(new ScoredCriterion(criterion.panel(), criterion.name(), criterion.description(),
                    criterion.weight(), roundOne(weightInPanel), roundOne(maxPoints), score, roundOne(earned),
                    given == null ? null : given.evidence()));
        }
        Double average = scored == 0 ? null : roundOne((double) scoreSum / scored);
        return new ScorecardPanelTally(input.panel(), input.share(), input.share(), roundOne(points), average, scored,
                criteria);
    }

    private static List<ScorecardCriterion> ofPanel(List<ScorecardCriterion> brief, CompetencyPanel panel) {
        return brief.stream().filter(criterion -> criterion.panel() == panel).toList();
    }

    static double roundOne(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record PanelInput(CompetencyPanel panel, List<ScorecardCriterion> criteria, int share) {}
}
