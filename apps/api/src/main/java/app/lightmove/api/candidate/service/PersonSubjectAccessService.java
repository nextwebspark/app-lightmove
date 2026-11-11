package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiUse;
import app.lightmove.api.candidate.model.SubjectAccessAbout;
import app.lightmove.api.candidate.model.SubjectAccessArchive;
import app.lightmove.api.candidate.model.SubjectAccessContribution;
import app.lightmove.api.candidate.model.SubjectAccessFile;
import app.lightmove.api.candidate.model.SubjectAccessPosition;
import app.lightmove.api.candidate.model.SubjectAccessReferences;
import app.lightmove.api.candidate.model.SubjectAccessSection;
import app.lightmove.api.candidate.model.SubjectAccessSubject;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything held on one person, as one zip, to answer their subject access request (#919, GDPR Art. 15 and 20):
 * each {@link SubjectAccessContributor}'s rows read whole, the ids in them named, and the fixed words on why the data
 * is held, who reads it, where it came from and how the AI works on it. An admin's ({@code PERSON_SUBJECT_ACCESS}),
 * audited by id and counts. A restricted person can still be exported: handing them their own data is not a use of it.
 */
@Service
@RequiredArgsConstructor
public class PersonSubjectAccessService {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Sections in the order a reader meets them; one a later contributor adds goes after these. */
    private static final List<String> SECTION_ORDER = List.of("person", "contacts", "tags", "photo", "positions",
            "scorecards", "notes", "documents", "documentVersions", "outreachRuns", "emails", "emailAttachments",
            "meetings", "recordings", "interviewPlans", "tasks", "timeline", "earlierContacts", "earlierPhotos");

    /** Where a record, a contact or a lookup came from, by the value its column stores (case aside). */
    private static final Map<String, String> SOURCES = sources();

    private final NamedParameterJdbcTemplate jdbc;
    private final List<SubjectAccessContributor> contributors;
    private final SubjectAccessArchiveWriter writer;
    private final AuditService audit;
    private final Clock clock;
    private final LightMoveProperties properties;

    @Transactional(readOnly = true)
    public SubjectAccessArchive archiveOf(UUID userId, UUID workspaceId, UUID personId,
                                          HttpServletRequest httpRequest) {
        MapSqlParameterSource scope = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personId", personId);
        String personName = jdbc.query("""
                SELECT full_name FROM app_lm_person WHERE workspace_id = :workspaceId AND id = :personId""", scope,
                rows -> rows.next() ? rows.getString("full_name") : null);
        if (personName == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        List<UUID> candidateIds = jdbc.queryForList("""
                SELECT id FROM app_lm_project_candidate WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY created_at, id""", scope, UUID.class);
        SubjectAccessSubject subject = new SubjectAccessSubject(workspaceId, personId, candidateIds);

        List<SubjectAccessSection> sections = new ArrayList<>();
        Set<AiUse> aiUses = EnumSet.noneOf(AiUse.class);
        List<SubjectAccessFile> files = new ArrayList<>();
        for (SubjectAccessContributor contributor : contributors) {
            SubjectAccessContribution contribution = contributor.contributionOf(subject);
            sections.addAll(contribution.sections());
            aiUses.addAll(contribution.aiUses());
            files.addAll(contribution.files());
        }
        sections.sort(Comparator.comparingInt(PersonSubjectAccessService::orderOf));

        WorkspaceFacts workspace = workspaceOf(workspaceId);
        SubjectAccessReferences references = referencesOf(workspaceId, sections);
        String exportedBy = Optional.ofNullable(references.colleagues().get(userId.toString()))
                .orElseGet(() -> nameOfUser(userId));
        SubjectAccessAbout about = aboutOf(workspace, references, mappedProjectIds(sections));
        SubjectAccessArchive archive = new SubjectAccessArchive(personName, workspace.name(), exportedBy,
                clock.instant(), about, List.copyOf(aiUses), sections, references, files);

        audit.event(ProjectEventType.PERSON_SUBJECT_ACCESS_EXPORTED)
                .actor(userId).workspace(workspaceId).target("person", personId).from(httpRequest)
                .detail("positions", candidateIds.size())
                .detail("notes", rowsIn(sections, "notes"))
                .detail("documents", rowsIn(sections, "documents"))
                .detail("messages", rowsIn(sections, "emails"))
                .detail("recordings", rowsIn(sections, "recordings"))
                .detail("tasks", rowsIn(sections, "tasks"))
                .record();
        return archive;
    }

    /** Writes the zip. Reads no row: it runs once the response streams, off the request's thread and scope. */
    public void write(SubjectAccessArchive archive, OutputStream target) throws IOException {
        writer.write(archive, target);
    }

    private static int orderOf(SubjectAccessSection section) {
        int index = SECTION_ORDER.indexOf(section.key());
        return index < 0 ? SECTION_ORDER.size() : index;
    }

    private static int rowsIn(List<SubjectAccessSection> sections, String key) {
        return sections.stream().filter(section -> section.key().equals(key))
                .mapToInt(section -> section.rows().size()).sum();
    }

    private WorkspaceFacts workspaceOf(UUID workspaceId) {
        return Objects.requireNonNull(jdbc.query("""
                SELECT w.name, w.mode, r.workspace_id IS NOT NULL AS retention_chosen, r.candidate_retention_months
                FROM app_lm_workspace w LEFT JOIN app_lm_workspace_retention r ON r.workspace_id = w.id
                WHERE w.id = :workspaceId""", new MapSqlParameterSource("workspaceId", workspaceId), rows -> {
                    if (!rows.next()) {
                        throw ApiException.of(ErrorCode.NOT_FOUND);
                    }
                    Integer months = rows.getObject("candidate_retention_months", Integer.class);
                    return new WorkspaceFacts(rows.getString("name"), "AGENCY".equals(rows.getString("mode")),
                            rows.getBoolean("retention_chosen") ? months : properties.retention().candidateMonths());
                }));
    }

    private String nameOfUser(UUID userId) {
        return Optional.ofNullable(jdbc.query("SELECT full_name FROM app_lm_user WHERE id = :userId",
                new MapSqlParameterSource("userId", userId),
                rows -> rows.next() ? rows.getString("full_name") : null)).orElse("An admin");
    }

    private SubjectAccessReferences referencesOf(UUID workspaceId, List<SubjectAccessSection> sections) {
        Set<UUID> ids = new LinkedHashSet<>();
        Set<UUID> projectIds = new LinkedHashSet<>();
        Set<UUID> statusIds = new LinkedHashSet<>();
        for (SubjectAccessSection section : sections) {
            for (Map<String, Object> row : section.rows()) {
                row.forEach((column, value) -> {
                    if (value instanceof String text && UUID_TEXT.matcher(text).matches()) {
                        UUID id = UUID.fromString(text);
                        ids.add(id);
                        if (column.equals("project_id")) {
                            projectIds.add(id);
                        } else if (column.equals("status_id")) {
                            statusIds.add(id);
                        }
                    }
                });
            }
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("ids", ids.toArray(UUID[]::new))
                .addValue("projectIds", projectIds.toArray(UUID[]::new))
                .addValue("statusIds", statusIds.toArray(UUID[]::new));
        Map<String, String> colleagues = new LinkedHashMap<>();
        jdbc.query("SELECT id, full_name FROM app_lm_user WHERE id = ANY (:ids) ORDER BY full_name", parameters,
                row -> {
                    colleagues.put(row.getString("id"), row.getString("full_name"));
                });
        Map<String, SubjectAccessPosition> positions = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.id, p.position_title, p.archived_at, c.name AS hiring_for
                FROM app_lm_project p LEFT JOIN app_lm_client c ON c.id = p.client_id
                WHERE p.workspace_id = :workspaceId AND p.id = ANY (:projectIds) ORDER BY p.created_at""", parameters,
                row -> {
                    Timestamp archived = row.getTimestamp("archived_at");
                    positions.put(row.getString("id"), new SubjectAccessPosition(row.getString("position_title"),
                            row.getString("hiring_for"), archived == null ? null : archived.toInstant().toString()));
                });
        Map<String, String> statuses = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, label FROM app_lm_workspace_candidate_status
                WHERE workspace_id = :workspaceId AND id = ANY (:statusIds)""", parameters, row -> {
                    statuses.put(row.getString("id"), row.getString("label"));
                });
        Map<String, Map<String, String>> customColumns = new LinkedHashMap<>();
        jdbc.query("""
                SELECT project_id, field_key, label FROM app_lm_project_custom_column
                WHERE project_id = ANY (:projectIds) ORDER BY project_id, display_order""", parameters, row -> {
                    customColumns.computeIfAbsent(row.getString("project_id"), ignored -> new LinkedHashMap<>())
                            .put(row.getString("field_key"), row.getString("label"));
                });
        return new SubjectAccessReferences(colleagues, positions, statuses, customColumns);
    }

    /** The positions the person is on now: a note or a timeline line may name one they have left. */
    private static Set<String> mappedProjectIds(List<SubjectAccessSection> sections) {
        Set<String> projectIds = new LinkedHashSet<>();
        sections.stream().filter(section -> section.key().equals("positions"))
                .flatMap(section -> section.rows().stream())
                .forEach(row -> projectIds.add(String.valueOf(row.get("project_id"))));
        return projectIds;
    }

    private SubjectAccessAbout aboutOf(WorkspaceFacts workspace, SubjectAccessReferences references,
                                       Set<String> mappedProjectIds) {
        String firm = workspace.name();
        String hiringSide = workspace.agency() ? "client" : "business unit";
        List<String> recipients = new ArrayList<>();
        recipients.add("The team at " + firm + ": the people who work on its candidates.");
        references.positions().forEach((projectId, position) -> {
            if (position.hiringFor() != null && mappedProjectIds.contains(projectId)) {
                recipients.add("The " + hiringSide + " " + position.hiringFor() + ", for the position "
                        + position.title() + ", through the people it has been given access to that position. They"
                        + " read your profile on it, with its status and package, but never the team's notes,"
                        + " assessments, emails or call records.");
            }
        });
        recipients.add("Uncava, which provides the software the record is kept in, and the services it runs on, on "
                + firm + "'s behalf.");
        String retention = workspace.retentionMonths() == null
                ? firm + " keeps records until they are deleted."
                : firm + " reviews a record for deletion once nobody has worked on it for " + workspace.retentionMonths()
                        + " months and it is on no live position.";
        long purgeDays = properties.notes().purgeAfter().toDays();
        return new SubjectAccessAbout(
                firm + " holds this record and decides how it is used. Uncava provides the software it is kept in.",
                firm + " uses this record for executive search: to find and assess people for the positions it is"
                        + " asked to fill" + (workspace.agency() ? " for its clients" : "") + ", and to approach them"
                        + " about those positions.",
                recipients, retention, SOURCES,
                "You can ask " + firm + " to correct anything here that is wrong, to delete your record, to restrict"
                        + " or stop its use of it, or to stop contacting you. person.json in this file is a copy you"
                        + " can take to another service. You can also complain to a data protection authority.",
                "A note the team deleted is kept for " + purgeDays + " days in case it was deleted by mistake, then"
                        + " deleted for good. Until then it is in this file, with the time it was deleted.",
                "Colleagues at " + firm + " are named where they wrote, changed or did something on your record."
                        + " Notes, emails and call transcripts are given as written.",
                AiUse.NO_SOLELY_AUTOMATED_DECISION);
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

    private record WorkspaceFacts(String name, boolean agency, Integer retentionMonths) {}
}
