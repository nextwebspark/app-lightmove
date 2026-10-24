package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import app.lightmove.api.notetaker.model.StatedFact;
import java.util.List;

/**
 * The call record's body, written from the summary by code so every record has the same sections in the same order.
 * It is read only by models, never drawn, so nothing is escaped: the prompts reading it treat it as data.
 */
final class CallRecordMarkdown {

    /** Raised whenever the summariser's record changes shape, so a record written by an older one can be told apart. */
    static final int VERSION = 1;

    private CallRecordMarkdown() {
    }

    static String of(MeetingSummary summary) {
        StringBuilder body = new StringBuilder();
        body.append(summary.overview().strip()).append("\n");
        if (summary.account() != null) {
            body.append("\n## Account of the call\n\n").append(summary.account().strip()).append("\n");
        }
        if (!summary.facts().isEmpty() || summary.motivation() != null || summary.packageSaid() != null
                || summary.notice() != null || summary.mobility() != null) {
            body.append("\n## Facts stated\n\n");
            fact(body, "Motivation", summary.motivation());
            fact(body, "Package", summary.packageSaid());
            fact(body, "Notice", summary.notice());
            fact(body, "Mobility", summary.mobility());
            for (StatedFact fact : summary.facts()) {
                body.append("- **").append(fact.topic()).append(":** ").append(fact.said());
                appendTime(body, fact.at());
                if (fact.byTeam()) {
                    body.append(" (said by the team, not the executive)");
                }
                body.append("\n");
            }
        }
        if (!summary.competencyEvidence().isEmpty() || !summary.competenciesNotCovered().isEmpty()) {
            body.append("\n## Evidence against the brief\n\n");
            for (CompetencyEvidence evidence : summary.competencyEvidence()) {
                body.append("- **").append(evidence.competency()).append("**");
                if (evidence.example() != null) {
                    body.append(evidence.example() ? " (with an example)" : " (a claim, no example)");
                }
                body.append(": ").append(evidence.evidence());
                appendTime(body, evidence.at());
                if (evidence.quote() != null) {
                    body.append(" Said: \"").append(evidence.quote()).append("\"");
                }
                body.append("\n");
            }
            if (!summary.competenciesNotCovered().isEmpty()) {
                body.append("- **Not covered:** ").append(String.join(", ", summary.competenciesNotCovered()))
                        .append("\n");
            }
        }
        section(body, "Key points", summary.keyPoints());
        section(body, "Concerns", summary.concerns());
        section(body, "Where it cuts against the brief", summary.briefConcerns());
        section(body, "Open questions", summary.openQuestions());
        section(body, "Next steps", summary.nextSteps());
        return body.toString().strip();
    }

    private static void fact(StringBuilder body, String label, String value) {
        if (value != null) {
            body.append("- **").append(label).append(":** ").append(value).append("\n");
        }
    }

    private static void appendTime(StringBuilder body, String at) {
        if (at != null) {
            body.append(" [").append(at).append("]");
        }
    }

    private static void section(StringBuilder body, String heading, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        body.append("\n## ").append(heading).append("\n\n");
        items.forEach(item -> body.append("- ").append(item).append("\n"));
    }
}
