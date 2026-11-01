package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.dto.ErasePersonRequest;
import app.lightmove.api.candidate.dto.PersonErasurePreviewResponse;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.storage.service.StorageDeletionQueue;
import app.lightmove.api.core.text.service.ConfirmationName;
import app.lightmove.api.core.text.service.LinkedInUrls;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a person (#918): the workspace's person and everything held about them, deleted for good in one
 * transaction — every mapping, note, document, contact, tag, timeline line, email, meeting, recording, task and bell
 * row, by the cascades V91 onwards put on {@code app_lm_person}, and what no cascade reaches through each
 * {@link PersonErasureContributor}. Their files are queued for the bucket in the same transaction, and, unless the
 * admin says otherwise, their profile and addresses go on the suppression list so no door files them again.
 *
 * <p>What stays, by design: the audit trail and the credit ledger are append-only and keep the person's id, never
 * their name (this erasure is audited by id and counts); and an erased person ages out of the backups within their
 * retention window rather than being rewritten there.
 */
@Service
@RequiredArgsConstructor
public class PersonErasureService {

    private static final String PERSON_TARGET = "person";

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonSuppressionService suppression;
    private final StorageDeletionQueue storageDeletions;
    private final List<PersonErasureContributor> contributors;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PersonErasurePreviewResponse preview(UUID workspaceId, UUID personId) {
        ErasingRow row = requirePerson(workspaceId, personId, false);
        Map<ErasedHolding, Long> holdings = holdingsOf(workspaceId, personId);
        String blockedBy = contributors.stream()
                .map(contributor -> contributor.blockerOf(workspaceId, personId))
                .flatMap(Optional::stream)
                .map(ErrorCode::name)
                .findFirst()
                .orElse(null);
        return new PersonErasurePreviewResponse(personId, row.fullName(),
                holdings.getOrDefault(ErasedHolding.POSITIONS, 0L), holdings.getOrDefault(ErasedHolding.NOTES, 0L),
                holdings.getOrDefault(ErasedHolding.DOCUMENTS, 0L), holdings.getOrDefault(ErasedHolding.FILES, 0L),
                holdings.getOrDefault(ErasedHolding.EMAILS, 0L), holdings.getOrDefault(ErasedHolding.OUTREACH_RUNS, 0L),
                holdings.getOrDefault(ErasedHolding.MEETINGS, 0L), holdings.getOrDefault(ErasedHolding.RECORDINGS, 0L),
                holdings.getOrDefault(ErasedHolding.TASKS, 0L), blockedBy);
    }

    @Transactional
    public void erase(UUID userId, UUID workspaceId, UUID personId, ErasePersonRequest request,
                      HttpServletRequest httpRequest) {
        ErasingRow row = requirePerson(workspaceId, personId, true);
        if (!ConfirmationName.matches(row.fullName(), request.confirmName())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "confirmName",
                    "Type the candidate's full name exactly to confirm");
        }
        Map<ErasedHolding, Long> holdings = holdingsOf(workspaceId, personId);
        MapSqlParameterSource person = personParameters(workspaceId, personId);
        List<Map<String, Object>> mappings = jdbc.queryForList("""
                SELECT c.id, c.project_id FROM app_lm_project_candidate c
                JOIN app_lm_person p ON p.id = c.person_id
                WHERE c.person_id = :personId AND p.workspace_id = :workspaceId
                """, person);
        String profileSlug = LinkedInUrls.profileSlugOrNull(row.linkedinUrl());
        ErasedPerson erased = new ErasedPerson(workspaceId, personId, row.fullName(), profileSlug,
                mappings.stream().map(mapping -> (UUID) mapping.get("id")).toList(),
                mappings.stream().map(mapping -> (UUID) mapping.get("project_id")).distinct().toList(), userId);

        contributors.forEach(contributor -> contributor.erase(erased));
        storageDeletions.enqueue(jdbc.queryForList("""
                SELECT storage_key FROM app_lm_person_document_version WHERE person_id = :personId
                """, person, String.class));
        boolean suppress = !Boolean.FALSE.equals(request.suppress());
        int suppressed = suppress ? suppression.suppress(workspaceId, userId,
                suppressionKeysOf(workspaceId, profileSlug, person), SuppressionReason.ERASED, null) : 0;

        jdbc.update("DELETE FROM app_lm_person WHERE id = :personId AND workspace_id = :workspaceId", person);

        AuditService.Builder event = audit.event(ProjectEventType.PERSON_ERASED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("suppressed", suppress)
                .detail("suppressionEntries", suppressed);
        holdings.forEach((holding, count) -> event.detail(holding.name().toLowerCase(Locale.ROOT), count));
        event.record();
    }

    private Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        MapSqlParameterSource person = personParameters(workspaceId, personId);
        Map<ErasedHolding, Long> holdings = new EnumMap<>(ErasedHolding.class);
        holdings.put(ErasedHolding.POSITIONS, count("""
                SELECT count(*) FROM app_lm_project_candidate WHERE person_id = :personId
                """, person));
        holdings.put(ErasedHolding.NOTES, count("""
                SELECT count(*) FROM app_lm_person_note
                WHERE person_id = :personId AND workspace_id = :workspaceId AND deleted_at IS NULL
                """, person));
        holdings.put(ErasedHolding.DOCUMENTS, count("""
                SELECT count(*) FROM app_lm_person_document WHERE person_id = :personId AND workspace_id = :workspaceId
                """, person));
        holdings.put(ErasedHolding.FILES, count("""
                SELECT count(*) FROM app_lm_person_document_version WHERE person_id = :personId
                """, person));
        contributors.forEach(contributor -> contributor.holdingsOf(workspaceId, personId)
                .forEach((holding, count) -> holdings.merge(holding, count, Long::sum)));
        return holdings;
    }

    private List<SuppressionKey> suppressionKeysOf(UUID workspaceId, String profileSlug,
                                                   MapSqlParameterSource person) {
        List<SuppressionKey> keys = new ArrayList<>();
        if (profileSlug != null) {
            keys.add(SuppressionKey.ofProfileSlug(workspaceId, profileSlug));
        }
        jdbc.queryForList("""
                SELECT value_key FROM app_lm_person_contact WHERE person_id = :personId AND channel = 'EMAIL'
                """, person, String.class).stream()
                .map(email -> SuppressionKey.ofEmailOrNull(workspaceId, email))
                .filter(Objects::nonNull)
                .forEach(keys::add);
        return keys;
    }

    /** {@code forUpdate}: an erasure holds the row so nothing maps, edits or writes to them while it runs. */
    private ErasingRow requirePerson(UUID workspaceId, UUID personId, boolean forUpdate) {
        List<ErasingRow> found = jdbc.query("""
                SELECT full_name, linkedin_url FROM app_lm_person WHERE id = :personId AND workspace_id = :workspaceId
                """ + (forUpdate ? " FOR UPDATE" : ""), personParameters(workspaceId, personId),
                (result, index) -> new ErasingRow(result.getString("full_name"), result.getString("linkedin_url")));
        if (found.isEmpty()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return found.getFirst();
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long counted = jdbc.queryForObject(sql, parameters, Long.class);
        return counted == null ? 0 : counted;
    }

    private static MapSqlParameterSource personParameters(UUID workspaceId, UUID personId) {
        return new MapSqlParameterSource("personId", personId).addValue("workspaceId", workspaceId);
    }

    private record ErasingRow(String fullName, String linkedinUrl) {}
}
