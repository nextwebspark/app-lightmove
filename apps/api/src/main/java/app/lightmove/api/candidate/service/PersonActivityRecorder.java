package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonActivity;
import app.lightmove.api.candidate.repository.PersonActivityRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * A change made through one mandate: the line names it. {@code detailPairs} alternate key and
     * value; a null value is left out, so a detail that does not apply need not be special-cased.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Candidate candidate, UUID actor, PersonActivityKind kind, Object... detailPairs) {
        String title = projects.findById(candidate.getProjectId()).map(Project::getPositionTitle).orElse(null);
        Person person = candidate.getPerson();
        activity.save(new PersonActivity(person.getWorkspaceId(), person.getId(), candidate.getProjectId(),
                title, actor, kind, detailsOf(detailPairs)));
    }

    private static Map<String, Object> detailsOf(Object... detailPairs) {
        Map<String, Object> details = new HashMap<>();
        for (int i = 0; i + 1 < detailPairs.length; i += 2) {
            if (detailPairs[i + 1] != null) {
                details.put((String) detailPairs[i], detailPairs[i + 1]);
            }
        }
        return details;
    }
}
