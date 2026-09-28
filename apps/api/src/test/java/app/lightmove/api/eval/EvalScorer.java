package app.lightmove.api.eval;

import app.lightmove.api.common.constant.NationalityGroup;
import app.lightmove.api.common.constant.Seniority;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scores a subject's predictions against the golden set and renders the checked-in report. Pure, so
 * the arithmetic is tested without a model; the report names categories and counts only — never a
 * person or a line of their profile.
 */
final class EvalScorer {

    static final String UNKNOWN = "Unknown";
    private static final String HIGH = "high";
    private static final String MEDIUM = "medium";

    private EvalScorer() {
    }

    static EvalScore score(List<GoldenRow> rows, List<EvalPrediction> predictions) {
        Map<String, EvalPrediction> byRow = predictions.stream()
                .collect(Collectors.toMap(EvalPrediction::rowId, Function.identity()));
        List<Pair> nationality = new ArrayList<>();
        List<Pair> seniority = new ArrayList<>();
        List<String> confidences = new ArrayList<>();
        Map<String, Integer> labelSources = new TreeMap<>();
        for (GoldenRow row : rows) {
            EvalPrediction predicted = byRow.get(row.id());
            labelSources.merge(row.labelSource() == null ? "unstated" : row.labelSource(), 1, Integer::sum);
            if (row.expected() != null && row.expected().nationality() != null) {
                nationality.add(new Pair(canonical(row.expected().nationality()),
                        predicted == null ? UNKNOWN : canonical(predicted.nationality())));
                confidences.add(predicted == null ? null : predicted.confidence());
            }
            if (row.expected() != null && row.expected().seniority() != null) {
                seniority.add(new Pair(row.expected().seniority(), predicted == null ? null : predicted.seniority()));
            }
        }
        return new EvalScore(nationality, confidences, seniority, labelSources);
    }

    /** Our spelling of a group, or Unknown for anything no group carries — the same fold the classifier makes. */
    static String canonical(String label) {
        NationalityGroup group = NationalityGroup.ofLabel(label);
        return group == null ? UNKNOWN : group.value();
    }

    static boolean isGcc(String label) {
        NationalityGroup group = NationalityGroup.ofLabel(label);
        return group != null && group.isGcc();
    }

    record Pair(String expected, String predicted) {

        boolean isHit() {
            return Objects.equals(expected, predicted);
        }
    }

    record CategoryScore(String category, int support, int predicted, int hits) {

        Double precision() {
            return predicted == 0 ? null : (double) hits / predicted;
        }

        Double recall() {
            return support == 0 ? null : (double) hits / support;
        }
    }

    record EvalScore(List<Pair> nationality, List<String> confidences, List<Pair> seniority,
                     Map<String, Integer> labelSources) {

        int rows() {
            return nationality.size();
        }

        Double accuracy() {
            return share(nationality.stream().filter(Pair::isHit).count(), rows());
        }

        boolean statesConfidence() {
            return confidences.stream().anyMatch(Objects::nonNull);
        }

        /** Of the answers made at high confidence, naming a group, the share that were right — what auto-fills. */
        Double precisionAtHigh() {
            long answered = 0;
            long right = 0;
            for (int index = 0; index < nationality.size(); index++) {
                Pair pair = nationality.get(index);
                if (HIGH.equals(confidences.get(index)) && !UNKNOWN.equals(pair.predicted())) {
                    answered++;
                    right += pair.isHit() ? 1 : 0;
                }
            }
            return share(right, answered);
        }

        int highAnswers() {
            int answered = 0;
            for (int index = 0; index < nationality.size(); index++) {
                if (HIGH.equals(confidences.get(index)) && !UNKNOWN.equals(nationality.get(index).predicted())) {
                    answered++;
                }
            }
            return answered;
        }

        /** Rows that are not a GCC national — an expat or genuinely Unknown — that were called one. */
        long gccFalsePositives() {
            return nationality.stream().filter(pair -> !isGcc(pair.expected()) && isGcc(pair.predicted())).count();
        }

        long nonGccRows() {
            return nationality.stream().filter(pair -> !isGcc(pair.expected())).count();
        }

        Double gccFalsePositiveRate() {
            return share(gccFalsePositives(), nonGccRows());
        }

        Double unknownRate() {
            return share(nationality.stream().filter(pair -> UNKNOWN.equals(pair.predicted())).count(), rows());
        }

        /** Share answered with a group at high or medium confidence; null for a subject that states none. */
        Double coverage() {
            if (!statesConfidence()) {
                return null;
            }
            long covered = 0;
            for (int index = 0; index < nationality.size(); index++) {
                String confidence = confidences.get(index);
                if (!UNKNOWN.equals(nationality.get(index).predicted())
                        && (HIGH.equals(confidence) || MEDIUM.equals(confidence))) {
                    covered++;
                }
            }
            return share(covered, rows());
        }

        List<CategoryScore> byCategory() {
            return categories().stream()
                    .map(category -> new CategoryScore(category,
                            (int) nationality.stream().filter(pair -> pair.expected().equals(category)).count(),
                            (int) nationality.stream().filter(pair -> pair.predicted().equals(category)).count(),
                            (int) nationality.stream()
                                    .filter(pair -> pair.isHit() && pair.expected().equals(category)).count()))
                    .toList();
        }

        /** Every label seen on either side, in the vocabulary's own order and Unknown last. */
        List<String> categories() {
            List<String> seen = nationality.stream()
                    .flatMap(pair -> Stream.of(pair.expected(), pair.predicted()))
                    .distinct()
                    .toList();
            List<String> ordered = new ArrayList<>();
            for (NationalityGroup group : NationalityGroup.values()) {
                if (seen.contains(group.value())) {
                    ordered.add(group.value());
                }
            }
            if (seen.contains(UNKNOWN)) {
                ordered.add(UNKNOWN);
            }
            return ordered;
        }

        Map<String, Map<String, Integer>> confusion() {
            Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
            for (String expected : categories()) {
                Map<String, Integer> row = new LinkedHashMap<>();
                for (String predicted : categories()) {
                    row.put(predicted, (int) nationality.stream()
                            .filter(pair -> pair.expected().equals(expected) && pair.predicted().equals(predicted))
                            .count());
                }
                matrix.put(expected, row);
            }
            return matrix;
        }

        boolean proposesSeniority() {
            return seniority.stream().anyMatch(pair -> pair.predicted() != null);
        }

        int seniorityRows() {
            return seniority.size();
        }

        Double seniorityExact() {
            return share(seniority.stream().filter(Pair::isHit).count(), seniorityRows());
        }

        /** Right, or one level away — "N-1" for "N-2", "C-Suite" for "N-1". */
        Double seniorityWithinOne() {
            return share(seniority.stream().filter(pair -> levelDistance(pair) <= 1).count(), seniorityRows());
        }

        private static int levelDistance(Pair pair) {
            Seniority expected = Seniority.fromValue(pair.expected());
            Seniority predicted = pair.predicted() == null ? null : Seniority.fromValue(pair.predicted());
            return expected == null || predicted == null ? Integer.MAX_VALUE
                    : Math.abs(expected.ordinal() - predicted.ordinal());
        }

        private static Double share(long part, long whole) {
            return whole == 0 ? null : (double) part / whole;
        }
    }

    static String markdown(String subject, String goldenSource, String runDate, EvalScore score) {
        StringBuilder out = new StringBuilder();
        out.append("## ").append(subject).append(" — ").append(runDate).append("\n\n");
        out.append("Golden set: ").append(goldenSource).append(", ").append(score.rows())
                .append(" rows labelled for nationality (label sources: ")
                .append(score.labelSources().entrySet().stream()
                        .map(entry -> entry.getKey() + " " + entry.getValue())
                        .collect(Collectors.joining(", ")))
                .append(").\n\n");
        out.append("| Metric | Value | Target |\n|---|---|---|\n");
        out.append("| Accuracy | ").append(percent(score.accuracy())).append(" | |\n");
        out.append("| Precision at `high` | ")
                .append(score.statesConfidence()
                        ? percent(score.precisionAtHigh()) + " (" + score.highAnswers() + " answers)"
                        : "n/a — states no confidence")
                .append(" | ≥ 95% |\n");
        out.append("| GCC false positives | ").append(score.gccFalsePositives()).append(" of ")
                .append(score.nonGccRows()).append(" (").append(percent(score.gccFalsePositiveRate()))
                .append(") | 0 |\n");
        out.append("| Unknown rate | ").append(percent(score.unknownRate())).append(" | |\n");
        out.append("| Coverage (high or medium) | ").append(percent(score.coverage())).append(" | |\n");
        if (score.proposesSeniority()) {
            out.append("| Seniority exact | ").append(percent(score.seniorityExact())).append(" (")
                    .append(score.seniorityRows()).append(" rows) | |\n");
            out.append("| Seniority within one level | ").append(percent(score.seniorityWithinOne()))
                    .append(" | |\n\n");
        } else {
            out.append("| Seniority | n/a — proposes none | |\n\n");
        }

        out.append("| Category | Support | Predicted | Precision | Recall |\n|---|---|---|---|---|\n");
        for (CategoryScore category : score.byCategory()) {
            out.append("| ").append(category.category()).append(" | ").append(category.support()).append(" | ")
                    .append(category.predicted()).append(" | ").append(percent(category.precision()))
                    .append(" | ").append(percent(category.recall())).append(" |\n");
        }

        List<String> categories = score.categories();
        out.append("\nConfusion (rows expected, columns predicted):\n\n| |");
        categories.forEach(category -> out.append(' ').append(category).append(" |"));
        out.append("\n|---|").append("---|".repeat(categories.size())).append('\n');
        score.confusion().forEach((expected, row) -> {
            out.append("| ").append(expected).append(" |");
            row.values().forEach(count -> out.append(' ').append(count == 0 ? "·" : count).append(" |"));
            out.append('\n');
        });
        return out.append('\n').toString();
    }

    static String percent(Double share) {
        return share == null ? "n/a" : String.format(Locale.ROOT, "%.1f%%", share * 100);
    }
}
