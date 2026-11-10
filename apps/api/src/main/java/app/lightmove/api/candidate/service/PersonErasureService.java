package app.lightmove.api.candidate.service;

import static app.lightmove.api.candidate.service.PersonErasureQueries.scopeOf;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a person (#918) in one transaction: the cascades from {@code app_lm_person}, each
 * {@link PersonErasureContributor}, their files queued and, unless unticked, their keys suppressed. The audit trail
 * and the credit ledger keep the id, never the name; backups age out rather than being rewritten.
 */
@Service
@RequiredArgsConstructor
public class PersonErasureService {

    private static final String PERSON_TARGET = "person";
    private static final String RETENTION_REASON = "retention";

    private final NamedParameterJdbcTemplate jdbc;
    private final SuppressionListService suppression;
    private final StorageDeletionQueue storageDeletions;
    private final List<PersonErasureContributor> contributors;
    private final PersonErasureHoldings erasureHoldings;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PersonErasurePreviewResponse preview(UUID workspaceId, UUID personId) {
        ErasingRow row = requirePerson(workspaceId, personId);
        Map<ErasedHolding, Long> holdings = erasureHoldings.holdingsOf(workspaceId, personId);
        String blockedBy = erasureHoldings.blockerOf(workspaceId, personId).map(ErrorCode::name).orElse(null);
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
        ErasingRow row = lockPerson(workspaceId, personId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!ConfirmationName.matches(row.fullName(), request.confirmName())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "confirmName",
                    "Type the candidate's full name exactly to confirm");
        }
        eraseLocked(userId, workspaceId, personId, row, !Boolean.FALSE.equals(request.suppress()), null,
                httpRequest);
    }

    /**
     * Candidate retention's delete (#921): the same erasure, of someone an admin reviewed as due, never suppressed —
     * passing the period is no objection. {@code stillDue} is asked once the person's row is held, since anything
     * mapping them or writing a line about them waits on that lock, so a person touched since the review is kept.
     *
     * @return false, erasing nothing, where the person is gone or no longer due
     */
    @Transactional
    public boolean eraseUnderRetention(UUID userId, UUID workspaceId, UUID personId, Predicate<UUID> stillDue,
                                       HttpServletRequest httpRequest) {
        Optional<ErasingRow> row = lockPerson(workspaceId, personId);
        if (row.isEmpty() || !stillDue.test(personId)) {
            return false;
        }
        eraseLocked(userId, workspaceId, personId, row.get(), false, RETENTION_REASON, httpRequest);
        return true;
    }

    private void eraseLocked(UUID userId, UUID workspaceId, UUID personId, ErasingRow row, boolean suppress,
                             String reason, HttpServletRequest httpRequest) {
        Map<ErasedHolding, Long> holdings = erasureHoldings.holdingsOf(workspaceId, personId);
        MapSqlParameterSource person = scopeOf(workspaceId, personId);
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
        int suppressed = suppress ? suppression.suppress(workspaceId, userId,
                suppressionKeysOf(profileSlug, person), SuppressionReason.ERASED, null) : 0;

        jdbc.update("DELETE FROM app_lm_person WHERE id = :personId AND workspace_id = :workspaceId", person);

        AuditService.Builder event = audit.event(ProjectEventType.PERSON_ERASED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detailIfPresent("reason", reason)
                .detail("suppressed", suppress)
                .detail("suppressionEntries", suppressed);
        holdings.forEach((holding, count) -> event.detail(holding.name().toLowerCase(Locale.ROOT), count));
        event.record();
    }

    private List<SuppressionKey> suppressionKeysOf(String profileSlug, MapSqlParameterSource person) {
        List<SuppressionKey> keys = new ArrayList<>();
        if (profileSlug != null) {
            keys.add(SuppressionKey.ofProfileSlug(profileSlug));
        }
        jdbc.queryForList("""
                SELECT value_key FROM app_lm_person_contact WHERE person_id = :personId AND channel = 'EMAIL'
                """, person, String.class).stream()
                .map(SuppressionKey::ofEmailOrNull)
                .filter(Objects::nonNull)
                .forEach(keys::add);
        return keys;
    }

    private ErasingRow requirePerson(UUID workspaceId, UUID personId) {
        return findPerson(workspaceId, personId, "").orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** An erasure holds the row, so nothing maps, edits or writes to them while it runs. */
    private Optional<ErasingRow> lockPerson(UUID workspaceId, UUID personId) {
        return findPerson(workspaceId, personId, " FOR UPDATE");
    }

    private Optional<ErasingRow> findPerson(UUID workspaceId, UUID personId, String lock) {
        return jdbc.query("""
                SELECT full_name, linkedin_url FROM app_lm_person WHERE id = :personId AND workspace_id = :workspaceId
                """ + lock, scopeOf(workspaceId, personId),
                (result, index) -> new ErasingRow(result.getString("full_name"), result.getString("linkedin_url")))
                .stream().findFirst();
    }

    private record ErasingRow(String fullName, String linkedinUrl) {}
}
