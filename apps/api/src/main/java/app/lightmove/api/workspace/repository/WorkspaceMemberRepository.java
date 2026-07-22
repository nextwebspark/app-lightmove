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
     * The user's workspace. Singular: a user has at most one <i>active</i> membership, enforced by a
     * partial unique index on {@code user_id WHERE status = 'ACTIVE'}.
     *
     * <p>Roles ride along eagerly: this row is what auth responses are assembled from, usually outside
     * a transaction, where a lazy collection would explode instead of loading.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<WorkspaceMember> findByUserIdAndStatus(UUID userId, MemberStatus status);

    /**
     * The tenant-isolation check, and the reason it takes both ids.
     *
     * <p>Asking "is this user a member of <i>this</i> workspace?" in one query is what stops a caller
     * naming someone else's workspace id and being served their data. Nothing workspace-scoped should
     * load without this returning an active member first.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndStatus(UUID workspaceId, UUID userId, MemberStatus status);

    @EntityGraph(attributePaths = "roles")
    List<WorkspaceMember> findByWorkspaceIdAndStatus(UUID workspaceId, MemberStatus status);

    /**
     * The staff roster — every member who is not a <b>pure</b> client representative. A member holding
     * only the CLIENT role is a client guest and must not surface among colleagues; a member who holds
     * CLIENT alongside a staff role is staff and does appear. The Team screen and its count use this.
     */
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

    /**
     * The membership's workspace-role names, straight from the assignment table. A projection rather
     * than a lazy walk because authorisation runs in {@code @PreAuthorize}, outside any transaction.
     */
    @Query("select r.name from WorkspaceMember m join m.roles r where m.id = :memberId")
    Set<String> findRoleNames(@Param("memberId") UUID memberId);

    /** The union of the membership's roles' actions — the answer authorisation actually wants. */
    @Query("""
            select a.name from WorkspaceMember m join m.roles r join r.actions a
            where m.id = :memberId
            """)
    Set<String> findActionNames(@Param("memberId") UUID memberId);

    /** Backs the last-admin guard: a workspace must never lose its only active ADMIN-role holder. */
    @Query("""
            select count(distinct m.id) from WorkspaceMember m join m.roles r
            where m.workspaceId = :workspaceId and m.status = :status and r.name = :roleName
            """)
    long countByRoleName(@Param("workspaceId") UUID workspaceId,
                         @Param("roleName") String roleName,
                         @Param("status") MemberStatus status);
}
