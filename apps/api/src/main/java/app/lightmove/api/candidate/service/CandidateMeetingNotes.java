package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.FiledMeetingNote;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonActivityRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Notetaker's seam into the people side: which position a recorded call belongs to, and its Meeting note. The
 * note is the person's like any other — staff-only, about one position or about the person — and its timeline line
 * says a call was recorded rather than that a note was typed.
 */
@Service
@RequiredArgsConstructor
public class CandidateMeetingNotes {

    private final PersonRepository people;
    private final CandidateRepository candidates;
    private final PersonNoteRepository notes;
    private final PersonActivityRepository activity;
    private final ProjectRepository projects;
    private final ProjectAccess projectAccess;
    private final PersonActivityRecorder recorder;

    /**
     * The position a call booked through Book a call (or its link) was booked on, read off the booking's timeline
     * line — a moved call's latest line carries its new time — while it is live and the consultant still works it.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> positionOfBookedCall(UUID userId, UUID workspaceId, UUID personId, Instant startsAt) {
        return activity.findPositionOfCallAt(workspaceId, personId, startsAt)
                .filter(projectId -> workable(userId, workspaceId, List.of(projectId)).contains(projectId))
                .filter(this::isLive);
    }

    /** The live positions mapping this person that the consultant may work. */
    @Transactional(readOnly = true)
    public List<UUID> livePositionsWorkedBy(UUID userId, UUID workspaceId, UUID personId) {
        List<UUID> mapping = candidates.findPositionsOfPerson(workspaceId, personId).stream()
                .map(Candidate::getProjectId)
                .distinct()
                .toList();
        Set<UUID> workable = workable(userId, workspaceId, mapping);
        return mapping.stream()
                .filter(workable::contains)
                .filter(this::isLive)
                .toList();
    }

    private Set<UUID> workable(UUID userId, UUID workspaceId, List<UUID> projectIds) {
        try {
            return projectAccess.projectsWithAction(userId, workspaceId, projectIds, ProjectAction.WORK_EXECUTE);
        } catch (ApiException noLongerAMember) {
            return Set.of();
        }
    }

    private boolean isLive(UUID projectId) {
        return projects.findById(projectId).filter(project -> !project.isArchived()).isPresent();
    }

    /**
     * Files the Meeting note: Markdown, by the consultant whose calendar sent the bot, about {@code projectId} or —
     * null — about the person. A position the person is no longer mapped on files about the person instead.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FiledMeetingNote> file(UUID workspaceId, UUID personId, UUID projectId, UUID authorUserId,
                                           String markdown, UUID recordingId) {
        Optional<Person> found = people.findByIdAndWorkspaceId(personId, workspaceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Person person = found.get();
        Candidate candidate = projectId == null ? null
                : candidates.findByProjectIdAndPersonId(projectId, personId).orElse(null);
        UUID aboutProjectId = candidate == null ? null : projectId;
        String title = aboutProjectId == null ? null
                : projects.findById(aboutProjectId).map(Project::getPositionTitle).orElse(null);
        PersonNote note = notes.save(PersonNote.written(person, aboutProjectId, title, PersonNoteKind.MEETING, markdown,
                NoteFormat.MARKDOWN, authorUserId));
        recorder.record(person, aboutProjectId, authorUserId, PersonActivityKind.MEETING_RECORDED,
                PersonActivityDetails.of("noteId", note.getId()).and("recordingId", recordingId));
        return Optional.of(new FiledMeetingNote(note.getId(), aboutProjectId,
                candidate == null ? null : candidate.getId()));
    }
}
