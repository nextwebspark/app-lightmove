package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.EmailTemplate;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every finder takes the workspace, and every one a person reads through also takes the reader: a personal template is
 * its owner's alone, a shared one the workspace's.
 */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    @Query("SELECT t FROM EmailTemplate t WHERE t.workspaceId = :workspaceId "
            + "AND (t.ownerUserId = :userId OR t.sharedAt IS NOT NULL) ORDER BY lower(t.name), t.createdAt")
    List<EmailTemplate> findVisibleTo(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    @Query("SELECT t FROM EmailTemplate t WHERE t.id = :id AND t.workspaceId = :workspaceId "
            + "AND (t.ownerUserId = :userId OR t.sharedAt IS NOT NULL)")
    Optional<EmailTemplate> findVisible(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                                        @Param("userId") UUID userId);

    /** The write's read: a shared template's owner and an admin saving together decide one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmailTemplate t WHERE t.id = :id AND t.workspaceId = :workspaceId "
            + "AND (t.ownerUserId = :userId OR t.sharedAt IS NOT NULL)")
    Optional<EmailTemplate> lockVisible(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                                        @Param("userId") UUID userId);

    long countByWorkspaceIdAndOwnerUserId(UUID workspaceId, UUID ownerUserId);

    List<EmailTemplate> findByWorkspaceIdAndIdInAndSharedAtIsNotNull(UUID workspaceId, Collection<UUID> ids);

    /** The templates a sender's own emails came from, the most recently written first. */
    @Query(value = """
            SELECT m.template_id FROM app_lm_outreach_message m
             WHERE m.workspace_id = :workspaceId AND m.sender_user_id = :userId AND m.template_id IS NOT NULL
             GROUP BY m.template_id
             ORDER BY max(m.created_at) DESC
             LIMIT :limit""", nativeQuery = true)
    List<UUID> findRecentlyUsedBy(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId,
                                  @Param("limit") int limit);
}
