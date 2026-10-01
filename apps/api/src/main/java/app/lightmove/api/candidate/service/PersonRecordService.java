package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.PersonPositionResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A workspace person as staff read them outside any one mandate, and the bridge from a mandate's row
 * to that person. Staff-only: a client seat reaches none of it.
 */
@Service
@RequiredArgsConstructor
public class PersonRecordService {

    private final PersonRepository people;
    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final UserRepository users;

    /** The person a mandate's row maps, after checking the mandate is the caller's workspace's. */
    @Transactional(readOnly = true)
    public UUID personOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.findPersonIdByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PersonRecordResponse recordOf(UUID workspaceId, UUID personId) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        return new PersonRecordResponse(person.getId(), person.getFullName(), person.getTitle(),
                person.getLinkedinUrl(), person.getLocationCity(), person.getLocationCountry(),
                CandidateResponseMapper.contactsOf(person), positionsOfPerson(workspaceId, personId, null));
    }

    /**
     * Every mandate mapping the person, oldest first, with {@code currentProjectId}'s first: the drawer
     * opened from that position leads with it.
     */
    @Transactional(readOnly = true)
    public List<PersonPositionResponse> positionsOf(UUID workspaceId, UUID personId, UUID currentProjectId) {
        people.requireInWorkspace(personId, workspaceId);
        return positionsOfPerson(workspaceId, personId, currentProjectId);
    }

    private List<PersonPositionResponse> positionsOfPerson(UUID workspaceId, UUID personId, UUID currentProjectId) {
        List<Candidate> mapped = candidates.findPositionsOfPerson(workspaceId, personId);
        Map<UUID, Project> mandates = projects.findAllById(mapped.stream().map(Candidate::getProjectId).toList())
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<UUID, User> filers = users.findAllById(mapped.stream().map(Candidate::getAddedBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return mapped.stream()
                .sorted(Comparator.comparing((Candidate row) -> !row.getProjectId().equals(currentProjectId)))
                .map(row -> {
                    Project mandate = mandates.get(row.getProjectId());
                    User filer = filers.get(row.getAddedBy());
                    return new PersonPositionResponse(row.getId(), row.getProjectId(),
                            mandate == null ? null : mandate.getPositionTitle(), row.getStatus().value(),
                            row.getAddedBy(), filer == null ? null : filer.getFullName(), row.getCreatedAt(),
                            row.getSource().value());
                })
                .toList();
    }
}
