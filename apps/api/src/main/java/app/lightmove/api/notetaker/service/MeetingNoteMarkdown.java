package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The Meeting note's body, written from the summary by code rather than by the model: the headings are always the
 * same, and the model's words are escaped so none of them becomes a link, a mention or a heading of its own.
 */
final class MeetingNoteMarkdown {

    /** {@code PersonNote}'s limit, less room for the marker that says the note was cut. */
    static final int LONGEST_BODY = 9_900;
    private static final String WORD_JOINER = "\u2060";
    /** {@code ://}, {@code www.} and {@code @}: what GFM's extended autolinks start from. */
    private static final Pattern LINK_TRIGGER = Pattern.compile("(?i)(:|www|@)(//|\\.|(?<=@))");

    private MeetingNoteMarkdown() {
    }

    /**
     * {@code stoppedAt} is the local time a recording that stopped early stopped, null for a whole call;
     * {@code missingMinutes} is how much of what was recorded could not be transcribed.
     */
    static String of(MeetingSummary summary, String stoppedAt, int missingMinutes) {
        return of(summary, stoppedAt, null, missingMinutes);
    }

    /** {@code resumedAt}: our bot stopped at {@code stoppedAt} and the backup recorder picked the call up then. */
    static String of(MeetingSummary summary, String stoppedAt, String resumedAt, int missingMinutes) {
        StringBuilder body = new StringBuilder();
        if (stoppedAt != null && resumedAt != null) {
            body.append("**Partial — recording stopped at ").append(stoppedAt).append(" and resumed at ")
                    .append(resumedAt).append(" by the backup recorder**\n\n");
        } else if (stoppedAt != null) {
            body.append("**Partial — recording stopped at ").append(stoppedAt).append("**\n\n");
        }
        if (missingMinutes > 0) {
            body.append("**Partial — about ").append(missingMinutes)
                    .append(missingMinutes == 1 ? " minute" : " minutes")
                    .append(" of the call could not be transcribed**\n\n");
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

    /**
     * Brackets make links and mentions, and a line-leading mark or number makes a heading or a list. GFM also links
     * a bare {@code https://…}, {@code www.…} or address with no brackets at all, and reads its triggers after
     * escapes are resolved, so each is broken with a word joiner instead: the words read the same, and link nowhere.
     */
    static String escape(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').strip();
        StringBuilder escaped = new StringBuilder(flat.length());
        int leadingDigits = 0;
        while (leadingDigits < flat.length() && Character.isDigit(flat.charAt(leadingDigits))) {
            leadingDigits++;
        }
        for (int index = 0; index < flat.length(); index++) {
            char character = flat.charAt(index);
            boolean listNumberEnd = index > 0 && index == leadingDigits && (character == '.' || character == ')');
            if ("\\[]<>*_`~|".indexOf(character) >= 0 || (index == 0 && "#+-=>".indexOf(character) >= 0)
                    || listNumberEnd) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return LINK_TRIGGER.matcher(escaped).replaceAll("$1" + WORD_JOINER + "$2");
    }
}
