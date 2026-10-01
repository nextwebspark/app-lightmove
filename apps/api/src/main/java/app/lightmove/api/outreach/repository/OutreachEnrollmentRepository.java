package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.SequenceEnrollmentCount;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutreachEnrollmentRepository extends JpaRepository<OutreachEnrollment, UUID> {

    List<OutreachEnrollment> findByProjectIdAndPersonIdInAndStatusIn(UUID projectId, Collection<UUID> personIds,
                                                                     Collection<EnrollmentStatus> statuses);

    boolean existsBySequenceId(UUID sequenceId);

    @Query("select e.sequenceId as sequenceId, count(e) as total from OutreachEnrollment e "
            + "where e.projectId = :projectId group by e.sequenceId")
    List<SequenceEnrollmentCount> countBySequenceOfProject(UUID projectId);
}
