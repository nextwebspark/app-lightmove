package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.project.service.ProjectOutreachCounter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Scheduled and active runs on one position, for the archive dialog's warning. */
@Component
@RequiredArgsConstructor
class ProjectOutreachCounterAdapter implements ProjectOutreachCounter {

    private final OutreachEnrollmentRepository enrollments;

    @Override
    public long countLiveRuns(UUID projectId) {
        return enrollments.countByProjectIdAndStatusIn(projectId, EnrollmentStatus.LIVE);
    }
}
