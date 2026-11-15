package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PrivacyNoticeChannel;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Undoing a privacy email the mail provider refused (#923, #1267): its link marked failed and the person put back to how
 * they were last told, so the Art. 14 record never claims an email nobody received.
 */
@Component
@RequiredArgsConstructor
class PrivacyEmailRefusals {

    private final PersonRepository people;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    /**
     * Its own transaction in the emails' workspace: past the pool's last thread this runs on the publishing thread
     * after its commit, where the committed transaction's connection is still bound.
     */
    void inOwnTransaction(UUID workspaceId, Runnable undo) {
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        WorkspaceScope.runInJoinedTransaction(workspaceId, () -> own.executeWithoutResult(status -> undo.run()));
    }

    Optional<Person> withdraw(UUID workspaceId, UUID linkId, UUID personId, Instant sentAt,
                              Instant previouslyNotifiedAt, PrivacyNoticeChannel previousChannel) {
        jdbc.update("UPDATE app_lm_person_privacy_link SET failed_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", linkId).addValue("now", Timestamp.from(clock.instant())));
        Optional<Person> person = people.findByIdAndWorkspaceId(personId, workspaceId);
        person.ifPresent(found -> found.withdrawRefusedNotice(sentAt, previouslyNotifiedAt, previousChannel));
        return person;
    }
}
