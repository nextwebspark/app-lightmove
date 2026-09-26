package app.lightmove.api.workspace.repository;

import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    /**
     * Oldest first; no singular by-user lookup, since a user may hold several (V81). Roles load eagerly —
     * auth responses are assembled outside a transaction, where lazy loading throws.
     */
    @EntityGraph(attributePaths = "roles")
    List<WorkspaceMember> findAllByUserIdAndStatusOrderByJoinedAtAsc(UUID userId, MemberStatus status);

    /**
     * The tenant-isolation check: both ids in one query, so a caller naming another workspace's id is
     * not served its data. Nothing workspace-scoped loads before this finds an active member.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndStatus(UUID workspaceId, UUID userId, MemberStatus status);

    /** Whatever its status — an invitation may be reactivating a removed member. */
    @EntityGraph(attributePaths = "roles")
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @EntityGraph(attributePaths = "roles")
    List<WorkspaceMember> findByWorkspaceIdAndStatus(UUID workspaceId, MemberStatus status);

    /** The staff roster: everyone but a <b>pure</b> client (CLIENT alongside a staff role is staff). */
    @EntityGraph(attributePaths = "roles")
    @Query("""
            select m from WorkspaceMember m
            where m.workspaceId = :workspaceId and m.status = :status
              and exists (select 1 from m.roles r where r.name <> :clientRole)
            """)
    List<WorkspaceMember> findStaff(@Param("workspaceId") UUID workspaceId,
                                    @Param("status") MemberStatus status,
                                    @Param("clientRole") String clientRole);

    long countByWorkspaceIdAndStatus(UUID workspaceId, MemberStatus status);

    @EntityGraph(attributePaths = "roles")
    Optional<WorkspaceMember> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** A projection, not a lazy walk: authorisation runs in {@code @PreAuthorize}, outside a transaction. */
    @Query("select r.name from WorkspaceMember m join m.roles r where m.id = :memberId")
    Set<String> findRoleNames(@Param("memberId") UUID memberId);

    /** The union of the membership's roles' actions. */
    @Query("""
            select a.name from WorkspaceMember m join m.roles r join r.actions a
            where m.id = :memberId
            """)
    Set<String> findActionNames(@Param("memberId") UUID memberId);

    /** Backs the last-admin guard. */
    @Query("""
            select count(distinct m.id) from WorkspaceMember m join m.roles r
            where m.workspaceId = :workspaceId and m.status = :status and r.name = :roleName
            """)
    long countByRoleName(@Param("workspaceId") UUID workspaceId,
                         @Param("roleName") String roleName,
                         @Param("status") MemberStatus status);
}
