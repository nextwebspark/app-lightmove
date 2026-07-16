package app.lightmove.api.project.repository;

import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.model.ProjectMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Seats on projects. Tenancy rides on member_id → workspace_member; callers scope the project first. */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    @EntityGraph(attributePaths = "roles")
    List<ProjectMember> findByProjectIdIn(Collection<UUID> projectIds);

    @EntityGraph(attributePaths = "roles")
    Optional<ProjectMember> findByProjectIdAndMemberId(UUID projectId, UUID memberId);

    /**
     * The union of the seat's roles' actions. A projection, not a lazy walk — authorisation runs in
     * {@code @PreAuthorize}, outside any transaction.
     */
    @Query("""
            select a.name from ProjectMember pm join pm.roles r join r.actions a
            where pm.id = :seatId
            """)
    Set<String> findActionNames(@Param("seatId") UUID seatId);

    /** Backs the last-admin guard: a project must never lose its only ADMIN-role seat. */
    @Query("""
            select count(distinct pm.id) from ProjectMember pm join pm.roles r
            where pm.projectId = :projectId and r.name = :roleName
            """)
    long countByRoleName(@Param("projectId") UUID projectId, @Param("roleName") String roleName);

    /**
     * Whether removing this workspace member would leave a live mandate without any project admin —
     * seats where this member holds ADMIN and nobody else on the project does. Delivered/closed
     * mandates don't count; blocking on finished work would make removal impossible over time.
     */
    @Query("""
            select count(distinct pm.id) from ProjectMember pm join pm.roles r, Project p
            where p.id = pm.projectId and pm.memberId = :memberId
              and r.name = 'ADMIN' and p.stage not in :doneStages
              and not exists (
                  select 1 from ProjectMember other join other.roles r2
                  where other.projectId = pm.projectId and other.id <> pm.id and r2.name = 'ADMIN')
            """)
    long countSoleAdminSeatsExcludingStages(@Param("memberId") UUID memberId,
                                            @Param("doneStages") Collection<ProjectStage> doneStages);

    @Modifying
    long deleteByMemberId(UUID memberId);
}
