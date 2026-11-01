package app.lightmove.api.outreach.service;

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
 * A person's outreach, as their erasure finds it. Their runs, emails and meetings go by cascade; what this adds is the
 * one race and the one leftover. An email the dispatcher has claimed is on its way to the mail service, so the
 * erasure is refused until it lands, never raced. And an email's attachments are copies under keys of their own, which
 * the person's documents never reach, so they are queued for the bucket here.
 */
@Component
@RequiredArgsConstructor
class PersonErasureOutreach implements PersonErasureContributor {

    private final NamedParameterJdbcTemplate jdbc;
    private final StorageDeletionQueue storageDeletions;

    @Override
    public Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        MapSqlParameterSource person = parameters(workspaceId, personId);
        return Map.of(
                ErasedHolding.EMAILS, count("app_lm_outreach_message", person),
                ErasedHolding.OUTREACH_RUNS, count("app_lm_outreach_enrollment", person),
                ErasedHolding.MEETINGS, count("app_lm_person_meeting", person));
    }

    @Override
    public Optional<ErrorCode> blockerOf(UUID workspaceId, UUID personId) {
        return sendingClaims(parameters(workspaceId, personId), false) > 0
                ? Optional.of(ErrorCode.PERSON_ERASE_SEND_IN_FLIGHT) : Optional.empty();
    }

    /**
     * Locks the person's runs and emails first: the dispatcher claims with {@code SKIP LOCKED}, so from here it passes
     * them by, and a claim it committed before the lock is seen and refused.
     */
    @Override
    public void erase(ErasedPerson erased) {
        MapSqlParameterSource person = parameters(erased.workspaceId(), erased.personId());
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

    private long count(String table, MapSqlParameterSource person) {
        Long counted = jdbc.queryForObject("SELECT count(*) FROM " + table
                + " WHERE person_id = :personId AND workspace_id = :workspaceId", person, Long.class);
        return counted == null ? 0 : counted;
    }

    private static MapSqlParameterSource parameters(UUID workspaceId, UUID personId) {
        return new MapSqlParameterSource("personId", personId).addValue("workspaceId", workspaceId);
    }
}
