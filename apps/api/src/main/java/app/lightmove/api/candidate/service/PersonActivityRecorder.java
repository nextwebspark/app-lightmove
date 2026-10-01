package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonActivity;
import app.lightmove.api.candidate.repository.PersonActivityRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a person's history in the transaction that makes the change, never after it. The audit
 * ledger is written asynchronously and may lose a line to protect the work it records; a timeline that
 * loses a line is simply wrong, so a failure here fails the change with it.
 */
@Component
@RequiredArgsConstructor
class PersonActivityRecorder {

    private final PersonActivityRepository activity;
    private final ProjectRepository projects;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Candidate candidate, UUID actor, PersonActivityKind kind) {
        record(candidate, actor, kind, PersonActivityDetails.none());
    }

    /** A change made through one mandate: the line names it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Candidate candidate, UUID actor, PersonActivityKind kind, PersonActivityDetails details) {
        record(candidate.getPerson(), candidate.getProjectId(), actor, kind, details);
    }

    /** A change to the person itself, written about a mandate or about none ({@code projectId} null). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Person person, UUID projectId, UUID actor, PersonActivityKind kind,
                       PersonActivityDetails details) {
        String title = projectId == null ? null
                : projects.findById(projectId).map(Project::getPositionTitle).orElse(null);
        activity.save(new PersonActivity(person.getWorkspaceId(), person.getId(), projectId, title, actor, kind,
                details.asMap()));
    }
}
