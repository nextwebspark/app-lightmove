package app.lightmove.api.project.repository;

import app.lightmove.api.project.constant.ProjectRole;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.model.ProjectMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Seats on projects. Tenancy rides on member_id → workspace_member; callers scope the project first. */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectIdIn(Collection<UUID> projectIds);

    Optional<ProjectMember> findByProjectIdAndMemberId(UUID projectId, UUID memberId);

    Optional<ProjectMember> findByProjectIdAndRole(UUID projectId, ProjectRole role);

    /** Whether removing this workspace member would leave a live mandate without its lead. */
    @Query("""
            select count(pm) from ProjectMember pm join Project p on p.id = pm.projectId
            where pm.memberId = :memberId and pm.role = app.lightmove.api.project.constant.ProjectRole.LEAD
              and p.stage not in :doneStages
            """)
    long countLeadsExcludingStages(@Param("memberId") UUID memberId,
                                   @Param("doneStages") Collection<ProjectStage> doneStages);

    @Modifying
    long deleteByMemberId(UUID memberId);
}
