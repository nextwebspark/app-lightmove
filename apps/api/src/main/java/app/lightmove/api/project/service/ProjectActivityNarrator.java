package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Turns a ledger row into the sentence the drawer shows.
 *
 * <p>A whitelist, deliberately: an event type nobody has written a phrase for is skipped, so a type
 * added later never surfaces as a raw enum name on a client's screen. The only thing interpolated
 * from an event's metadata is a count, parsed as a number first — everything else the ledger holds
 * stays in the ledger.
 */
@Component
class ProjectActivityNarrator {

    private static final Map<ProjectEventType, ActivityPhrase> PHRASES = Map.ofEntries(
            Map.entry(ProjectEventType.PROJECT_CREATED, ActivityPhrase.of("started the mandate")),
            Map.entry(ProjectEventType.PROJECT_UPDATED, ActivityPhrase.of("updated the mandate")),
            Map.entry(ProjectEventType.PROJECT_TEAM_CHANGED, ActivityPhrase.of("changed the project team")),

            Map.entry(ProjectEventType.TRIAGE_COMPANY_ADDED, ActivityPhrase.of("added a company")),
            Map.entry(ProjectEventType.TRIAGE_COMPANY_CAPTURED, ActivityPhrase.of("captured a company")),
            Map.entry(ProjectEventType.TRIAGE_COMPANY_MOVED, ActivityPhrase.of("moved a company to another stage")),
            Map.entry(ProjectEventType.TRIAGE_COMPANY_EDITED, ActivityPhrase.of("edited a company")),
            Map.entry(ProjectEventType.TRIAGE_COMPANY_REMOVED, ActivityPhrase.of("removed a company")),
            Map.entry(ProjectEventType.TRIAGE_BULK_ADDED,
                    ActivityPhrase.counted("filed companies in bulk", "filed %d companies", "added")),

            Map.entry(ProjectEventType.CANDIDATE_ADDED, ActivityPhrase.of("mapped an executive")),
            Map.entry(ProjectEventType.CANDIDATE_UPDATED, ActivityPhrase.of("updated an executive")),
            Map.entry(ProjectEventType.CANDIDATE_REMOVED, ActivityPhrase.of("removed an executive")),
            Map.entry(ProjectEventType.CANDIDATE_CONTACT_LOOKED_UP,
                    ActivityPhrase.of("found contact details for an executive")),

            Map.entry(ProjectEventType.SPREADSHEET_IMPORTED,
                    ActivityPhrase.counted("imported a spreadsheet", "imported %d rows from a spreadsheet",
                            "rowsRead")),
            Map.entry(ProjectEventType.COMPANIES_EXPORTED, ActivityPhrase.of("exported a stage")),

            Map.entry(ProjectEventType.POSITION_UPDATED, ActivityPhrase.of("updated the brief")),
            Map.entry(ProjectEventType.POSITION_PUBLISHED, ActivityPhrase.of("published the brief")),
            Map.entry(ProjectEventType.POSITION_PUBLICATION_WITHDRAWN, ActivityPhrase.of("reopened the brief")),
            Map.entry(ProjectEventType.POSITION_TEMPLATE_APPLIED,
                    ActivityPhrase.of("redrafted the brief from a template")),
            Map.entry(ProjectEventType.POSITION_DOCUMENT_ATTACHED,
                    ActivityPhrase.of("attached the position description")),
            Map.entry(ProjectEventType.POSITION_DOCUMENT_REMOVED,
                    ActivityPhrase.of("removed the position description")),
            Map.entry(ProjectEventType.POSITION_DOCUMENT_EXTRACTED,
                    ActivityPhrase.of("read the position description")),

            Map.entry(ProjectEventType.STRATEGY_UPDATED, ActivityPhrase.of("changed the market filter")),
            Map.entry(ProjectEventType.STRATEGY_SEARCH_SAVED, ActivityPhrase.of("saved a search")),
            Map.entry(ProjectEventType.STRATEGY_SEARCH_DELETED, ActivityPhrase.of("deleted a saved search")));

    /** Null for an event the feed does not narrate, which the caller drops. */
    String narrate(String eventType, Map<String, Object> metadata) {
        ProjectEventType type = projectEventOrNull(eventType);
        ActivityPhrase phrase = type == null ? null : PHRASES.get(type);
        return phrase == null ? null : phrase.render(metadata);
    }

    private static ProjectEventType projectEventOrNull(String eventType) {
        try {
            return ProjectEventType.valueOf(eventType);
        } catch (IllegalArgumentException outsideThisDomain) {
            // The ledger holds auth and workspace events under the same column; they never target a
            // project, so this is a guard rather than a case that happens.
            return null;
        }
    }

    private record ActivityPhrase(String plain, String counted, String countKey) {

        static ActivityPhrase of(String plain) {
            return new ActivityPhrase(plain, null, null);
        }

        static ActivityPhrase counted(String plain, String counted, String countKey) {
            return new ActivityPhrase(plain, counted, countKey);
        }

        String render(Map<String, Object> metadata) {
            Long count = countKey == null ? null : countIn(metadata);
            return count == null ? plain : counted.formatted(count);
        }

        private Long countIn(Map<String, Object> metadata) {
            Object recorded = metadata.get(countKey);
            if (recorded == null) {
                return null;
            }
            try {
                return Long.parseLong(recorded.toString());
            } catch (NumberFormatException notACount) {
                return null;
            }
        }
    }
}
