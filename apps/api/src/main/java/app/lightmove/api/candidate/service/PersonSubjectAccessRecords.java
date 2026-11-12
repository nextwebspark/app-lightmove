package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiRunScope;
import app.lightmove.api.candidate.constant.AiUse;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.StoredDocumentFile;
import app.lightmove.api.candidate.model.SubjectAccessContribution;
import app.lightmove.api.candidate.model.SubjectAccessFile;
import app.lightmove.api.candidate.model.SubjectAccessSection;
import app.lightmove.api.candidate.model.SubjectAccessSubject;
import app.lightmove.api.core.export.service.RowSnapshotReader;
import app.lightmove.api.core.export.service.ZipArchiveWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The person's own tables in a subject access export: the record, contacts, tags, notes (a deleted one too, until
 * it is purged), documents, the timeline, every mapping with V91's frozen copy on it, and the scorecards. Only
 * machinery is withheld — the row version, a storage key, a lookup key derived from a value already exported.
 */
@Component
@RequiredArgsConstructor
class PersonSubjectAccessRecords implements SubjectAccessContributor {

    private static final Set<String> BACKGROUND_KEYS = Set.of("gender", "yearsExperience", "seniority");

    private final RowSnapshotReader rows;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Set<String> tables() {
        return Set.of("app_lm_person", "app_lm_person_photo", "app_lm_person_contact", "app_lm_person_tag",
                "app_lm_person_note", "app_lm_person_document", "app_lm_person_document_version",
                "app_lm_person_activity", "app_lm_project_candidate", "app_lm_candidate_contact",
                "app_lm_candidate_photo", "app_lm_candidate_scorecard");
    }

    @Override
    public SubjectAccessContribution contributionOf(SubjectAccessSubject subject) {
        Map<String, Object> parameters = subject.parameters();
        List<SubjectAccessSection> sections = new ArrayList<>();
        List<Map<String, Object>> person = rows.rowsOf("""
                SELECT * FROM app_lm_person WHERE workspace_id = :workspaceId AND id = :personId""",
                parameters, Set.of("workspace_id", "version"));
        sections.add(new SubjectAccessSection("person", "Your record", person));
        sections.add(new SubjectAccessSection("contacts", "Contact details", rows.rowsOf("""
                SELECT * FROM app_lm_person_contact WHERE person_id = :personId ORDER BY channel, found_at""",
                parameters, Set.of("value_key"))));
        sections.add(new SubjectAccessSection("tags", "Tags", rows.rowsOf("""
                SELECT t.label, t.retired_at FROM app_lm_person_tag pt
                JOIN app_lm_workspace_candidate_tag t ON t.id = pt.tag_id
                WHERE pt.person_id = :personId ORDER BY t.label""", parameters, Set.of())));
        List<SubjectAccessFile> files = new ArrayList<>();
        sections.add(new SubjectAccessSection("photo", "Photo", rows.rowsOf("""
                SELECT * FROM app_lm_person_photo WHERE person_id = :personId""",
                parameters, Set.of("version"), Set.of("content"), (row, binaries) ->
                        files.add(SubjectAccessFile.ofBytes("photo" + extensionOf(row.get("content_type")),
                                binaries.get("content"))))));
        sections.add(new SubjectAccessSection("notes", "Notes", rows.rowsOf("""
                SELECT * FROM app_lm_person_note WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY created_at, id""", parameters, Set.of("workspace_id", "version"))));
        sections.add(new SubjectAccessSection("documents", "Documents", rows.rowsOf("""
                SELECT * FROM app_lm_person_document WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY created_at, id""", parameters, Set.of("workspace_id", "version", "name_key"))));
        sections.add(new SubjectAccessSection("documentVersions", "Document files", rows.rowsOf("""
                SELECT * FROM app_lm_person_document_version WHERE person_id = :personId
                ORDER BY document_id, version_no""", parameters, Set.of("storage_key"))));
        List<Map<String, Object>> timeline = rows.rowsOf("""
                SELECT * FROM app_lm_person_activity WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY occurred_at, id""", parameters, Set.of("workspace_id"));
        sections.add(new SubjectAccessSection("timeline", "Timeline", timeline));
        List<Map<String, Object>> positions = rows.rowsOf("""
                SELECT * FROM app_lm_project_candidate WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY created_at, id""", parameters, Set.of("workspace_id", "version"));
        sections.add(new SubjectAccessSection("positions", "Positions", positions));
        List<Map<String, Object>> scorecards = rows.rowsOf("""
                SELECT * FROM app_lm_candidate_scorecard
                WHERE workspace_id = :workspaceId AND candidate_id = ANY (:candidateIds)
                ORDER BY created_at, id""", parameters, Set.of("workspace_id", "version"));
        sections.add(new SubjectAccessSection("scorecards", "Assessments", scorecards));
        sections.add(new SubjectAccessSection("earlierContacts", "Earlier copy of your contact details",
                rows.rowsOf("""
                        SELECT * FROM app_lm_candidate_contact WHERE candidate_id = ANY (:candidateIds)
                        ORDER BY candidate_id, channel, found_at""", parameters, Set.of("value_key"))));
        sections.add(new SubjectAccessSection("earlierPhotos", "Earlier copy of your photo", rows.rowsOf("""
                SELECT * FROM app_lm_candidate_photo WHERE candidate_id = ANY (:candidateIds) ORDER BY created_at""",
                parameters, Set.of("version"), Set.of("content"), (row, binaries) ->
                        files.add(SubjectAccessFile.ofBytes("earlier-copies/photo"
                                + extensionOf(row.get("content_type")), binaries.get("content"))))));
        files.addAll(documentFilesOf(parameters));
        return new SubjectAccessContribution(sections, aiUsesOf(person, positions, scorecards, timeline), files);
    }

    private static Set<AiUse> aiUsesOf(List<Map<String, Object>> person, List<Map<String, Object>> positions,
                                       List<Map<String, Object>> scorecards, List<Map<String, Object>> timeline) {
        Set<AiUse> uses = EnumSet.noneOf(AiUse.class);
        for (Map<String, Object> row : concat(person, positions)) {
            if (row.get("ai_nationality_reading") != null || inferred(row).contains("nationality")) {
                uses.add(AiUse.NATIONALITY);
            }
            if (inferred(row).stream().anyMatch(BACKGROUND_KEYS::contains)) {
                uses.add(AiUse.BACKGROUND);
            }
            if (row.get("ai_assessment") != null) {
                uses.add(AiUse.EARLIER_ASSESSMENT);
            }
        }
        if (scorecards.stream().anyMatch(row -> row.get("ai_drafted_at") != null)) {
            uses.add(AiUse.ASSESSMENT_DRAFT);
        }
        if (timeline.stream().anyMatch(PersonSubjectAccessRecords::isBackgroundRun)) {
            uses.add(AiUse.BACKGROUND);
        }
        return uses;
    }

    /**
     * The flag in {@code ai_inferred_fields} goes once a researcher confirms or changes the value, but the run's
     * timeline line stays, so a value the model proposed is still explained after it was confirmed. The line is
     * written for a nationality reading alone too: naming the background model then over-explains, never hides.
     */
    private static boolean isBackgroundRun(Map<String, Object> line) {
        return PersonActivityKind.AI_ASSESSED.name().equals(line.get("kind"))
                && line.get("details") instanceof JsonNode details
                && AiRunScope.BACKGROUND.name().equals(details.path("scope").asString(null));
    }

    private static List<String> inferred(Map<String, Object> row) {
        if (!(row.get("ai_inferred_fields") instanceof JsonNode fields) || !fields.isArray()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        fields.forEach(field -> keys.add(field.asString()));
        return keys;
    }

    private static List<Map<String, Object>> concat(List<Map<String, Object>> first, List<Map<String, Object>> second) {
        List<Map<String, Object>> both = new ArrayList<>(first);
        both.addAll(second);
        return both;
    }

    private List<SubjectAccessFile> documentFilesOf(Map<String, Object> parameters) {
        List<SubjectAccessFile> files = new ArrayList<>();
        jdbc.query("""
                SELECT DISTINCT ON (v.document_id) v.id, d.category, v.file_name, v.content_type, v.size_bytes,
                       v.storage_key
                FROM app_lm_person_document d
                JOIN app_lm_person_document_version v ON v.document_id = d.id
                WHERE d.workspace_id = :workspaceId AND d.person_id = :personId
                ORDER BY v.document_id, v.version_no DESC""", parameters, row -> {
                    String fileName = row.getString("file_name");
                    files.add(SubjectAccessFile.ofStored("documents/"
                            + ZipArchiveWriter.slugOf(row.getString("category"), "document") + "-"
                            + ZipArchiveWriter.fileNameOf(fileName, "document"), row.getObject("id", UUID.class),
                            new StoredDocumentFile(fileName, row.getString("content_type"),
                                    row.getLong("size_bytes"), row.getString("storage_key"), false)));
                });
        return files;
    }

    private static String extensionOf(Object contentType) {
        return switch (contentType == null ? "" : contentType.toString()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
