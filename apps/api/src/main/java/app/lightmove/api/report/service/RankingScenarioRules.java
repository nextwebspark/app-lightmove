package app.lightmove.api.report.service;

import app.lightmove.api.candidate.model.ScorecardCriterion;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.report.model.BriefWeighting;
import app.lightmove.api.report.model.CompetencyWeight;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a ranking scenario may hold, read against the brief as it stands. Weights are normalised within a panel when
 * scored, so "each group totals 100%" is the screen's rule for a group someone changed; a group left as the brief has
 * it passes whatever its own weights sum to, since the brief's never had to.
 */
final class RankingScenarioRules {

    static final int PANEL_TOTAL = 100;

    private RankingScenarioRules() {}

    /**
     * The brief with one competency per match key. Two rows named alike (case and spacing aside) share one score on
     * the scorecard, so the drawer counts them as one competency carrying both weights, and so does the ranking.
     */
    static List<ScorecardCriterion> uniqueOf(List<ScorecardCriterion> brief) {
        Map<String, ScorecardCriterion> byKey = new LinkedHashMap<>();
        brief.forEach(criterion -> byKey.merge(criterion.matchKey(), criterion, (held, again) ->
                new ScorecardCriterion(held.panel(), held.name(), held.description(), held.weight() + again.weight())));
        return List.copyOf(byKey.values());
    }

    static Map<String, Integer> briefWeightsOf(List<ScorecardCriterion> brief) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        brief.forEach(criterion -> weights.putIfAbsent(criterion.matchKey(), criterion.weight()));
        return weights;
    }

    static BriefWeighting weightingOf(List<ScorecardCriterion> brief, int technicalShare) {
        Map<String, ScorecardCriterion> byKey = new LinkedHashMap<>();
        brief.forEach(criterion -> byKey.putIfAbsent(criterion.matchKey(), criterion));
        return new BriefWeighting(technicalShare, byKey.values().stream()
                .map(criterion -> new CompetencyWeight(criterion.matchKey(), criterion.name(), criterion.weight()))
                .toList());
    }

    /**
     * Refuses weights that name other competencies than the brief's — it changed under the screen — or change a
     * group without bringing it to 100.
     */
    static void refuseUnfit(Map<String, Integer> weights, List<ScorecardCriterion> brief) {
        Map<String, Integer> briefWeights = briefWeightsOf(brief);
        if (!weights.keySet().equals(briefWeights.keySet())) {
            throw ApiException.of(ErrorCode.RANKING_BRIEF_CHANGED);
        }
        for (CompetencyPanel panel : CompetencyPanel.values()) {
            Set<String> keys = keysOf(brief, panel);
            boolean changed = keys.stream().anyMatch(key -> !Objects.equals(weights.get(key), briefWeights.get(key)));
            int total = keys.stream().mapToInt(weights::get).sum();
            if (changed && total != PANEL_TOTAL) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "weights",
                        "Each group's competency weights must total 100%");
            }
        }
    }

    static boolean isBaseline(int technicalShare, Map<String, Integer> weights, List<ScorecardCriterion> brief,
                              int briefShare) {
        return technicalShare == briefShare && weights.equals(briefWeightsOf(brief));
    }

    /**
     * A saved scenario's weights on today's brief. Only what the team changed is the scenario's: a competency it left
     * at the brief's weight, or never weighed, follows the brief as it stands.
     */
    static Map<String, Integer> weightsOnto(Map<String, Integer> saved, BriefWeighting against,
                                            List<ScorecardCriterion> brief) {
        Map<String, Integer> was = weightsOf(against);
        Map<String, Integer> onto = new LinkedHashMap<>();
        briefWeightsOf(brief).forEach((key, weight) -> {
            Integer kept = saved.get(key);
            onto.put(key, kept == null || kept.equals(was.get(key)) ? weight : kept);
        });
        return onto;
    }

    /** A saved share that the team left at the brief's follows the brief's, as an untouched weight does. */
    static int shareOnto(int saved, BriefWeighting against, int briefShare) {
        return saved == against.technicalShare() ? briefShare : saved;
    }

    /** Saved at the brief's own weighting: the scenario was cleared, and reads as no scenario at all. */
    static boolean isCleared(int savedShare, Map<String, Integer> saved, BriefWeighting against) {
        return savedShare == against.technicalShare() && saved.equals(weightsOf(against));
    }

    /** Order aside: dragging the brief's competencies about changes nothing a scenario weighed. */
    static boolean briefChangedSince(BriefWeighting against, List<ScorecardCriterion> brief, int briefShare) {
        BriefWeighting now = weightingOf(brief, briefShare);
        return now.technicalShare() != against.technicalShare()
                || !Set.copyOf(now.competencies()).equals(Set.copyOf(against.competencies()));
    }

    /** The competencies a scenario weighed that the brief no longer has, by the name they had. */
    static List<String> droppedSince(BriefWeighting against, List<ScorecardCriterion> brief) {
        Set<String> current = briefWeightsOf(brief).keySet();
        return against.competencies().stream()
                .filter(competency -> !current.contains(competency.key()))
                .map(CompetencyWeight::name)
                .toList();
    }

    static long changedCount(Map<String, Integer> weights, List<ScorecardCriterion> brief) {
        Map<String, Integer> briefWeights = briefWeightsOf(brief);
        return briefWeights.keySet().stream()
                .filter(key -> !Objects.equals(weights.get(key), briefWeights.get(key)))
                .count();
    }

    private static Map<String, Integer> weightsOf(BriefWeighting weighting) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weighting.competencies().forEach(competency -> weights.putIfAbsent(competency.key(), competency.weight()));
        return weights;
    }

    private static Set<String> keysOf(List<ScorecardCriterion> brief, CompetencyPanel panel) {
        return brief.stream()
                .filter(criterion -> criterion.panel() == panel)
                .map(ScorecardCriterion::matchKey)
                .collect(Collectors.toSet());
    }
}
