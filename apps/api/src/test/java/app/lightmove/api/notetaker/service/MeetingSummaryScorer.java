package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scores meeting notes against what a golden call is known to contain, by counts only — never a word of a call:
 * the facts the note must carry (motivation, package, notice, mobility), the points it must mention, what it must not
 * claim (a figure or a promise nobody said), and which of the brief's competencies it found evidence for.
 */
final class MeetingSummaryScorer {

    private MeetingSummaryScorer() {
    }

    /** What one golden call expects; every phrase is matched case-insensitively as a substring. */
    record Expected(Map<String, List<String>> facts, List<String> mustMention, List<String> mustNotMention,
                    List<String> covered, List<String> notCovered) {}

    record Score(int factsExpected, int factsFound, int mentionsExpected, int mentionsFound, int inventions,
                 int competenciesExpected, int competenciesRight) {

        static Score empty() {
            return new Score(0, 0, 0, 0, 0, 0, 0);
        }

        Score plus(Score other) {
            return new Score(factsExpected + other.factsExpected, factsFound + other.factsFound,
                    mentionsExpected + other.mentionsExpected, mentionsFound + other.mentionsFound,
                    inventions + other.inventions, competenciesExpected + other.competenciesExpected,
                    competenciesRight + other.competenciesRight);
        }
    }

    static Score score(Expected expected, MeetingSummary summary) {
        int factsExpected = 0;
        int factsFound = 0;
        for (Map.Entry<String, List<String>> fact : expected.facts().entrySet()) {
            factsExpected++;
            String said = factOf(summary, fact.getKey());
            if (said != null && fact.getValue().stream().anyMatch(phrase -> contains(said, phrase))) {
                factsFound++;
            }
        }
        String whole = wholeOf(summary);
        int mentionsFound = (int) expected.mustMention().stream().filter(phrase -> contains(whole, phrase)).count();
        int inventions = (int) expected.mustNotMention().stream().filter(phrase -> contains(whole, phrase)).count();
        List<String> evidenced = summary.competencyEvidence().stream().map(CompetencyEvidence::competency).toList();
        int competenciesRight = (int) expected.covered().stream().filter(name -> anyIs(evidenced, name)).count()
                + (int) expected.notCovered().stream()
                        .filter(name -> anyIs(summary.competenciesNotCovered(), name) && !anyIs(evidenced, name))
                        .count();
        return new Score(factsExpected, factsFound, expected.mustMention().size(), mentionsFound, inventions,
                expected.covered().size() + expected.notCovered().size(), competenciesRight);
    }

    static String markdown(String source, String model, String date, int calls, Score score) {
        return """

                ## %s — %s, %s

                | Calls | Facts carried | Points mentioned | Inventions | Competencies placed right |
                |---|---|---|---|---|
                | %d | %d / %d | %d / %d | %d | %d / %d |
                """.formatted(date, source, model, calls, score.factsFound(), score.factsExpected(),
                score.mentionsFound(), score.mentionsExpected(), score.inventions(), score.competenciesRight(),
                score.competenciesExpected());
    }

    private static String factOf(MeetingSummary summary, String fact) {
        return switch (fact) {
            case "motivation" -> summary.motivation();
            case "package" -> summary.packageSaid();
            case "notice" -> summary.notice();
            case "mobility" -> summary.mobility();
            default -> throw new IllegalArgumentException("No fact called " + fact);
        };
    }

    private static String wholeOf(MeetingSummary summary) {
        List<String> parts = new ArrayList<>();
        parts.add(summary.overview());
        parts.addAll(summary.keyPoints());
        parts.add(summary.motivation());
        parts.add(summary.packageSaid());
        parts.add(summary.notice());
        parts.add(summary.mobility());
        parts.addAll(summary.concerns());
        parts.addAll(summary.nextSteps());
        summary.competencyEvidence().forEach(evidence -> parts.add(evidence.evidence()));
        parts.addAll(summary.briefConcerns());
        return String.join("\n", parts.stream().filter(part -> part != null).toList());
    }

    private static boolean anyIs(List<String> names, String name) {
        return names.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(name));
    }

    private static boolean contains(String text, String phrase) {
        return text.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT));
    }
}
