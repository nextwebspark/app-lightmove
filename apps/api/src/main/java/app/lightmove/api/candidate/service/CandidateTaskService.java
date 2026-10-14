package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.TaskSubject;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code task}'s way into the people side: who a task is about, the names a task list draws, and the timeline
 * line each change to a task writes in its own transaction. Nothing here depends back on {@code task}.
 */
@Service
@RequiredArgsConstructor
public class CandidateTaskService {

    private final CandidateRepository candidates;
    private final PersonRepository persons;
    private final PersonNoteRepository notes;
    private final PersonActivityRecorder activity;

    /** The executive behind a row of the position; a row outside it is a 404. */
    @Transactional(readOnly = true)
    public TaskSubject requireSubject(UUID projectId, UUID candidateId) {
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        return subjectOf(candidate);
    }

    /** The executive's row on the position, if they are still mapped there. */
    @Transactional(readOnly = true)
    public Optional<TaskSubject> findSubject(UUID projectId, UUID candidateId) {
        return candidateId == null ? Optional.empty()
                : candidates.findByIdAndProjectId(candidateId, projectId).map(CandidateTaskService::subjectOf);
    }

    /** A linked follow-up's note must be one of the person's own; anything else is a 404. */
    @Transactional(readOnly = true)
    public void requireNoteAbout(UUID workspaceId, UUID personId, UUID noteId) {
        notes.requireOnPerson(noteId, workspaceId, personId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> personNamesOf(UUID workspaceId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return persons.findByWorkspaceIdAndIdIn(workspaceId, personIds).stream()
                .filter(person -> person.getFullName() != null)
                .collect(Collectors.toMap(Person::getId, Person::getFullName));
    }

    /**
     * One line of the person's timeline about a task, naming it by its title as it stood. A person the
     * workspace no longer holds has no timeline to write to.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTaskLine(UUID workspaceId, UUID projectId, UUID personId, UUID actor, PersonActivityKind kind,
                               Map<String, Object> details) {
        persons.findByIdAndWorkspaceId(personId, workspaceId).ifPresent(person -> {
            PersonActivityDetails line = PersonActivityDetails.none();
            details.forEach(line::and);
            activity.record(person, projectId, actor, kind, line);
        });
    }

    private static TaskSubject subjectOf(Candidate candidate) {
        return new TaskSubject(candidate.getId(), candidate.getPerson().getId(),
                candidate.getPerson().getFullName());
    }
}
