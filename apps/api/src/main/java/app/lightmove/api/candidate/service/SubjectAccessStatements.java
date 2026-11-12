package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiUse;
import app.lightmove.api.candidate.model.SubjectAccessAbout;
import app.lightmove.api.candidate.model.SubjectAccessPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The fixed words a subject access export opens with (GDPR Art. 15(1)(a)–(h)), kept in one place for counsel (#930)
 * and #922 to revise. Only the firm's own facts are filled in; nothing here reads a row.
 */
final class SubjectAccessStatements {

    /** Where a record, a contact or a lookup came from, by the value its column stores (case aside). */
    static final Map<String, String> SOURCES = sources();

    private SubjectAccessStatements() {
    }

    static SubjectAccessAbout aboutOf(Firm firm, Map<String, SubjectAccessPosition> positions,
                                      Set<String> mappedProjectIds, long deletedNotePurgeDays) {
        String name = firm.name();
        return new SubjectAccessAbout(
                name + " holds this record and decides how it is used. Uncava provides the software it is kept in.",
                name + " uses this record for executive search: to find and assess people for the positions it is"
                        + " asked to fill" + (firm.agency() ? " for its clients" : "") + ", and to approach them"
                        + " about those positions.",
                recipientsOf(firm, positions, mappedProjectIds),
                firm.retentionMonths() == null
                        ? name + " keeps records until they are deleted."
                        : name + " reviews a record for deletion once nobody has worked on it for "
                                + firm.retentionMonths() + " months and it is on no live position.",
                SOURCES,
                "You can ask " + name + " to correct anything here that is wrong, to delete your record, to restrict"
                        + " or stop its use of it, or to stop contacting you. person.json in this file is a copy you"
                        + " can take to another service. You can also complain to a data protection authority.",
                "A note the team deleted is kept for " + deletedNotePurgeDays + " days in case it was deleted by"
                        + " mistake, then deleted for good. Until then it is in this file, with the time it was"
                        + " deleted.",
                "Colleagues at " + name + " are named where they wrote, changed or did something on your record."
                        + " Notes, emails and call transcripts are given as written.",
                AiUse.NO_SOLELY_AUTOMATED_DECISION);
    }

    /**
     * An archived position is offered to no client seat (V137), so its hiring side is named as having read the
     * profile while the position was open, never as reading it now.
     */
    private static List<String> recipientsOf(Firm firm, Map<String, SubjectAccessPosition> positions,
                                             Set<String> mappedProjectIds) {
        String hiringSide = firm.agency() ? "client" : "business unit";
        List<String> recipients = new ArrayList<>();
        recipients.add("The team at " + firm.name() + ": the people who work on its candidates.");
        positions.forEach((projectId, position) -> {
            if (position.hiringFor() == null || !mappedProjectIds.contains(projectId)) {
                return;
            }
            String who = "The " + hiringSide + " " + position.hiringFor() + ", for the position " + position.title()
                    + ", through the people it has been given access to that position.";
            String withheld = " but never the team's notes, assessments, emails or call records.";
            recipients.add(position.archivedAt() == null
                    ? who + " They read your profile on it, with its status and package," + withheld
                    : who + " While the position was open, until " + position.archivedAt().substring(0, 10) + ", they could"
                            + " read your profile on it, with its status and package," + withheld
                            + " The position is closed and they no longer can.");
        });
        recipients.add("Uncava, which provides the software the record is kept in, and the services it runs on, on "
                + firm.name() + "'s behalf.");
        return recipients;
    }

    private static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("manual", "Typed in by someone on the team.");
        sources.put("csv", "Imported from a spreadsheet the team uploaded.");
        sources.put("extension", "Read from a public profile page, such as LinkedIn, by the team's browser plugin.");
        sources.put("ai_sourced", "Found by Find executives, in a people-search provider's index of public"
                + " professional profiles.");
        sources.put("people_search", "Found through People Search, in a people-search provider's index of public"
                + " professional profiles.");
        sources.put("contactout", "ContactOut, a provider of professional profiles and contact details.");
        sources.put("brightdata", "Bright Data, a provider of public professional profiles.");
        sources.put("harvestapi", "HarvestAPI, which reads public professional profiles.");
        return Collections.unmodifiableMap(sources);
    }

    /** The firm's facts the words name: what it is called, whether it is an agency, and its retention period. */
    record Firm(String name, boolean agency, @Nullable Integer retentionMonths) {}
}
