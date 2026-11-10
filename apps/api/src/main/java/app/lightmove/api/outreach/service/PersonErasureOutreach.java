package app.lightmove.api.outreach.service;

import static app.lightmove.api.candidate.service.PersonErasureQueries.count;
import static app.lightmove.api.candidate.service.PersonErasureQueries.scopeOf;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.storage.service.StorageDeletionQueue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A person's outreach. The rows go by cascade; an email mid-send refuses the erasure rather than racing it, and an
 * email's attachments, copies under keys of their own, are queued for the bucket.
 */
@Component
@RequiredArgsConstructor
class PersonErasureOutreach implements PersonErasureContributor {

    private final NamedParameterJdbcTemplate jdbc;
    private final StorageDeletionQueue storageDeletions;

    @Override
    public Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        MapSqlParameterSource person = scopeOf(workspaceId, personId);
        return Map.of(
                ErasedHolding.EMAILS, count(jdbc, """
                        SELECT count(*) FROM app_lm_outreach_message
                        WHERE person_id = :personId AND workspace_id = :workspaceId
                        """, person),
                ErasedHolding.OUTREACH_RUNS, count(jdbc, """
                        SELECT count(*) FROM app_lm_outreach_enrollment
                        WHERE person_id = :personId AND workspace_id = :workspaceId
                        """, person),
                ErasedHolding.MEETINGS, count(jdbc, """
                        SELECT count(*) FROM app_lm_person_meeting
                        WHERE person_id = :personId AND workspace_id = :workspaceId
                        """, person));
    }

    @Override
    public Optional<ErrorCode> blockerOf(UUID workspaceId, UUID personId) {
        return sendingClaims(scopeOf(workspaceId, personId), false) > 0
                ? Optional.of(ErrorCode.PERSON_ERASE_SEND_IN_FLIGHT) : Optional.empty();
    }

    /**
     * Locks the person's runs and emails first: the dispatcher claims with {@code SKIP LOCKED}, so from here it passes
     * them by, and a claim it committed before the lock is seen and refused.
     */
    @Override
    public void erase(ErasedPerson erased) {
        MapSqlParameterSource person = scopeOf(erased.workspaceId(), erased.personId());
        if (sendingClaims(person, true) > 0) {
            throw ApiException.of(ErrorCode.PERSON_ERASE_SEND_IN_FLIGHT);
        }
        List<String> attachmentKeys = jdbc.queryForList("""
                SELECT a.storage_key FROM app_lm_outreach_message_attachment a
                JOIN app_lm_outreach_message m ON m.id = a.message_id
                WHERE m.person_id = :personId AND m.workspace_id = :workspaceId
                """, person, String.class);
        storageDeletions.enqueue(attachmentKeys);
    }

    private int sendingClaims(MapSqlParameterSource person, boolean lock) {
        String forUpdate = lock ? " FOR UPDATE" : "";
        List<Boolean> enrollments = jdbc.queryForList("""
                SELECT sending_since IS NOT NULL FROM app_lm_outreach_enrollment
                WHERE person_id = :personId AND workspace_id = :workspaceId""" + forUpdate, person, Boolean.class);
        List<Boolean> messages = jdbc.queryForList("""
                SELECT sending_since IS NOT NULL FROM app_lm_outreach_message
                WHERE person_id = :personId AND workspace_id = :workspaceId""" + forUpdate, person, Boolean.class);
        return (int) (enrollments.stream().filter(Boolean::booleanValue).count()
                + messages.stream().filter(Boolean::booleanValue).count());
    }
}
