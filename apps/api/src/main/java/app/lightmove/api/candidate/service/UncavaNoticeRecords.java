package app.lightmove.api.candidate.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Which version of Uncava's notice to executives (#1490) a person was last sent, written only once an email carrying
 * its link was delivered. The columns are not mapped on {@code Person}, so an entity flush never writes them back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UncavaNoticeRecords {

    private final NamedParameterJdbcTemplate jdbc;
    private final LightMoveProperties properties;
    private final PlatformTransactionManager transactionManager;

    /** Inside the send that delivered the email, so the record commits with it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDelivered(UUID workspaceId, Collection<UUID> personIds, Instant deliveredAt) {
        update(workspaceId, personIds, deliveredAt);
    }

    /**
     * A mailer's, after its refusal records committed, so neither can roll the other back. Past the pool's last thread
     * this runs on the publishing thread after its commit, where the committed transaction's connection is still bound.
     */
    public void recordDeliveredInOwnTransaction(UUID workspaceId, Collection<UUID> personIds, Instant deliveredAt) {
        if (versionCarried() == null || personIds.isEmpty()) {
            return;
        }
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            WorkspaceScope.runInJoinedTransaction(workspaceId,
                    () -> own.executeWithoutResult(status -> update(workspaceId, personIds, deliveredAt)));
        } catch (RuntimeException failed) {
            log.error("Workspace {}: recording Uncava's notice on {} delivered emails failed", workspaceId,
                    personIds.size(), failed);
        }
    }

    /** The latest delivery wins; an older one arriving late never winds the record back. */
    private void update(UUID workspaceId, Collection<UUID> personIds, Instant deliveredAt) {
        String version = versionCarried();
        if (version == null || personIds.isEmpty()) {
            return;
        }
        jdbc.update("""
                UPDATE app_lm_person SET uncava_notice_version = :version, uncava_notice_sent_at = :at
                WHERE workspace_id = :workspaceId AND id IN (:personIds)
                  AND (uncava_notice_sent_at IS NULL OR uncava_notice_sent_at <= :at)
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personIds", personIds)
                .addValue("version", version)
                .addValue("at", Timestamp.from(deliveredAt)));
    }

    /** Null while no notice is published. */
    private String versionCarried() {
        return properties.legal().candidateNoticeVersion();
    }
}
