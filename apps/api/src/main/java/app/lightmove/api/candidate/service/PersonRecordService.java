package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.DoNotContactResponse;
import app.lightmove.api.candidate.dto.PersonPositionResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.StoredPhoto;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonPhotoRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final PersonPhotoRepository photos;
    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final ProjectAccess projectAccess;

    /** The person a mandate's row maps, after checking the mandate is the caller's workspace's. */
    @Transactional(readOnly = true)
    public UUID personOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.findPersonIdByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** The person's stored photo, for the Candidates page; a stranger's id and no photo are the same 404. */
    @Transactional(readOnly = true)
    public StoredPhoto photoOf(UUID workspaceId, UUID personId) {
        if (!people.existsByIdAndWorkspaceId(personId, workspaceId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return photos.findByPersonId(personId)
                .map(photo -> new StoredPhoto(photo.getContent(), photo.getContentType()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PersonRecordResponse recordOf(UUID userId, UUID workspaceId, UUID personId) {
        return toRecord(userId, people.requireInWorkspace(personId, workspaceId));
    }

    /** The same read for a person the caller already holds, after a change made through this service's peers. */
    PersonRecordResponse toRecord(UUID userId, Person person) {
        List<Candidate> mapped = candidates.findPositionsOfPerson(person.getWorkspaceId(), person.getId());
        Map<UUID, User> named = usersOf(Stream.concat(Stream.of(person.getCreatedBy(), person.getDoNotContactSetBy()),
                mapped.stream().map(Candidate::getAddedBy)).toList());
        User filer = named.get(person.getCreatedBy());
        return new PersonRecordResponse(person.getId(), person.getFullName(), person.getTitle(),
                employerOf(person, mapped),
                person.getSeniorityLevel() == null ? null : person.getSeniorityLevel().value(),
                person.getLinkedinUrl(), person.getProfile().enrichedAt(), person.getLocationCity(),
                person.getLocationCountry(),
                person.getNationality(), person.getGender() == null ? null : person.getGender().value(),
                person.getYearsExperience(), person.getSummary(),
                CandidateResponseMapper.compensationOf(person), CandidateResponseMapper.careerOf(person),
                CandidateResponseMapper.contactsOf(person),
                positionsOf(userId, person.getWorkspaceId(), mapped, null, named),
                person.getOwnerUserId(), doNotContactOf(person, named), List.copyOf(person.getTagIds()),
                person.getSource().value(), person.getCreatedAt(), person.getCreatedBy(),
                filer == null ? null : filer.getFullName());
    }

    /**
     * Every mandate mapping the person. Opened from a position, that one leads and the rest follow
     * oldest first; opened from the workspace's Candidates page, the newest leads.
     */
    @Transactional(readOnly = true)
    public List<PersonPositionResponse> positionsOf(UUID userId, UUID workspaceId, UUID personId,
                                                    UUID currentProjectId) {
        people.requireInWorkspace(personId, workspaceId);
        List<Candidate> mapped = candidates.findPositionsOfPerson(workspaceId, personId);
        return positionsOf(userId, workspaceId, mapped, currentProjectId,
                usersOf(mapped.stream().map(Candidate::getAddedBy).toList()));
    }

    /**
     * The employer the Candidates page names beside a person: what their most recent position recorded,
     * else the first post of their career.
     */
    static String employerOf(Person person, List<Candidate> mappedOldestFirst) {
        for (int index = mappedOldestFirst.size() - 1; index >= 0; index--) {
            String recorded = mappedOldestFirst.get(index).getCompanyName();
            if (recorded != null && !recorded.isBlank()) {
                return recorded;
            }
        }
        return person.getProfile().career().stream()
                .map(CandidateCareerEntry::company).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private List<PersonPositionResponse> positionsOf(UUID userId, UUID workspaceId, List<Candidate> mapped,
                                                     UUID currentProjectId, Map<UUID, User> named) {
        List<UUID> projectIds = mapped.stream().map(Candidate::getProjectId).distinct().toList();
        Map<UUID, Project> mandates = projects.findAllById(projectIds)
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Set<UUID> workable = projectAccess.projectsWithAction(userId, workspaceId, projectIds,
                ProjectAction.WORK_EXECUTE);
        Comparator<Candidate> order = currentProjectId == null
                ? Comparator.comparing(Candidate::getCreatedAt).reversed()
                : Comparator.comparing((Candidate row) -> !row.getProjectId().equals(currentProjectId));
        return mapped.stream()
                .sorted(order)
                .map(row -> {
                    Project mandate = mandates.get(row.getProjectId());
                    User filer = named.get(row.getAddedBy());
                    return new PersonPositionResponse(row.getId(), row.getProjectId(),
                            mandate == null ? null : mandate.getPositionTitle(), row.getStatus().value(),
                            row.getAddedBy(), filer == null ? null : filer.getFullName(), row.getCreatedAt(),
                            row.getSource().value(), workable.contains(row.getProjectId()));
                })
                .toList();
    }

    private static DoNotContactResponse doNotContactOf(Person person, Map<UUID, User> named) {
        if (!person.isDoNotContact()) {
            return null;
        }
        User setter = person.getDoNotContactSetBy() == null ? null : named.get(person.getDoNotContactSetBy());
        return new DoNotContactResponse(person.getDoNotContactReason(), person.getDoNotContactSetBy(),
                setter == null ? null : setter.getFullName(), person.getDoNotContactSetAt());
    }

    private Map<UUID, User> usersOf(Collection<UUID> ids) {
        return users.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
