package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import java.util.List;

/**
 * The Meeting note's body, written from the summary by code rather than by the model: the headings are always the
 * same, and the model's words are escaped so none of them becomes a link, a mention or a heading of its own.
 */
final class MeetingNoteMarkdown {

    /** {@code PersonNote}'s limit, less room for the marker that says the note was cut. */
    static final int LONGEST_BODY = 9_900;

    private MeetingNoteMarkdown() {
    }

    /** {@code stoppedAt} is the local time a partial recording stopped, null for a whole call. */
    static String of(MeetingSummary summary, String stoppedAt) {
        StringBuilder body = new StringBuilder();
        if (stoppedAt != null) {
            body.append("**Partial — recording stopped at ").append(stoppedAt).append("**\n\n");
        }
        body.append(escape(summary.overview())).append("\n");
        section(body, "Key points", summary.keyPoints());
        if (summary.motivation() != null || summary.packageSaid() != null || summary.notice() != null
                || summary.mobility() != null) {
            body.append("\n## What they said\n\n");
            fact(body, "Motivation", summary.motivation());
            fact(body, "Package", summary.packageSaid());
            fact(body, "Notice", summary.notice());
            fact(body, "Mobility", summary.mobility());
        }
        section(body, "Concerns", summary.concerns());
        if (!summary.competencyEvidence().isEmpty() || !summary.competenciesNotCovered().isEmpty()
                || !summary.briefConcerns().isEmpty()) {
            body.append("\n## Against the brief\n\n");
            for (CompetencyEvidence evidence : summary.competencyEvidence()) {
                body.append("- **").append(escape(evidence.competency())).append(":** ")
                        .append(escape(evidence.evidence()));
                if (evidence.at() != null) {
                    body.append(" (").append(escape(evidence.at())).append(")");
                }
                body.append("\n");
            }
            if (!summary.competenciesNotCovered().isEmpty()) {
                body.append("- **Not covered:** ").append(escape(String.join(", ", summary.competenciesNotCovered())))
                        .append("\n");
            }
            for (String concern : summary.briefConcerns()) {
                body.append("- ").append(escape(concern)).append("\n");
            }
        }
        section(body, "Next steps", summary.nextSteps());
        String text = body.toString().strip();
        return text.length() <= LONGEST_BODY ? text : text.substring(0, LONGEST_BODY).strip() + "\n\n…";
    }

    private static void section(StringBuilder body, String heading, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        body.append("\n## ").append(heading).append("\n\n");
        items.forEach(item -> body.append("- ").append(escape(item)).append("\n"));
    }

    private static void fact(StringBuilder body, String label, String value) {
        if (value != null) {
            body.append("- **").append(label).append(":** ").append(escape(value)).append("\n");
        }
    }

    /** Brackets make links and mentions, and a line-leading mark makes a heading or a list. */
    static String escape(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').strip();
        StringBuilder escaped = new StringBuilder(flat.length());
        for (int index = 0; index < flat.length(); index++) {
            char character = flat.charAt(index);
            if ("\\[]<>*_`~|".indexOf(character) >= 0 || (index == 0 && "#+-=>".indexOf(character) >= 0)) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
