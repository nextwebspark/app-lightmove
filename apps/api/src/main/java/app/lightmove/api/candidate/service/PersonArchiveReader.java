package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.ArchivedDocumentFile;
import app.lightmove.api.candidate.model.ArchivedNote;
import app.lightmove.api.candidate.model.ArchivedPerson;
import app.lightmove.api.candidate.model.StoredDocumentFile;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Everyone the workspace holds, a page at a time, as an export of the whole workspace writes them (#926): every live
 * note, whichever position it is about, and the latest file of each document. Staff-only material, so nothing reaches
 * it but an admin's export; it reads in the caller's workspace scope.
 */
@Service
@RequiredArgsConstructor
public class PersonArchiveReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonDocumentService documents;

    /** In the order they joined, so pages of a growing pool never skip anyone. */
    public List<UUID> personIdsOf(UUID workspaceId) {
        return jdbc.queryForList("""
                SELECT id FROM app_lm_person WHERE workspace_id = :workspaceId ORDER BY created_at, id""",
                new MapSqlParameterSource("workspaceId", workspaceId), UUID.class);
    }

    public List<ArchivedPerson> archiveOf(UUID workspaceId, List<UUID> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personIds", personIds.toArray(UUID[]::new));
        Map<UUID, String> names = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, full_name FROM app_lm_person
                WHERE workspace_id = :workspaceId AND id = ANY (:personIds) ORDER BY created_at, id""", parameters,
                row -> {
                    names.put(row.getObject("id", UUID.class), row.getString("full_name"));
                });
        Map<UUID, List<ArchivedNote>> notes = new HashMap<>();
        jdbc.query("""
                SELECT n.person_id, n.kind, n.project_title, u.full_name AS author, n.created_at, n.edited_at,
                       n.pinned, n.body
                FROM app_lm_person_note n LEFT JOIN app_lm_user u ON u.id = n.author_user_id
                WHERE n.workspace_id = :workspaceId AND n.person_id = ANY (:personIds) AND n.deleted_at IS NULL
                ORDER BY n.person_id, n.created_at""", parameters, row -> {
                    Timestamp edited = row.getTimestamp("edited_at");
                    notes.computeIfAbsent(row.getObject("person_id", UUID.class), ignored -> new ArrayList<>())
                            .add(new ArchivedNote(row.getString("kind"), row.getString("project_title"),
                                    row.getString("author"), row.getTimestamp("created_at").toInstant(),
                                    edited == null ? null : edited.toInstant(), row.getBoolean("pinned"),
                                    row.getString("body")));
                });
        Map<UUID, List<ArchivedDocumentFile>> files = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (v.document_id) d.person_id, d.category, d.title, v.version_no, v.file_name,
                       v.content_type, v.size_bytes, v.storage_key
                FROM app_lm_person_document d
                JOIN app_lm_person_document_version v ON v.document_id = d.id
                WHERE d.workspace_id = :workspaceId AND d.person_id = ANY (:personIds)
                ORDER BY v.document_id, v.version_no DESC""", parameters, row -> {
                    files.computeIfAbsent(row.getObject("person_id", UUID.class), ignored -> new ArrayList<>())
                            .add(new ArchivedDocumentFile(row.getString("category"), row.getString("title"),
                                    row.getInt("version_no"), new StoredDocumentFile(row.getString("file_name"),
                                    row.getString("content_type"), row.getLong("size_bytes"),
                                    row.getString("storage_key"), false)));
                });
        return names.entrySet().stream()
                .map(person -> new ArchivedPerson(person.getKey(), person.getValue(),
                        notes.getOrDefault(person.getKey(), List.of()),
                        files.getOrDefault(person.getKey(), List.of())))
                .toList();
    }

    public InputStream open(ArchivedDocumentFile document) {
        return documents.open(document.file());
    }
}
