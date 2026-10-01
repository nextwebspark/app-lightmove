package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.dto.BulkPeopleChangeResponse;
import app.lightmove.api.candidate.dto.CandidatePoolResponse;
import app.lightmove.api.candidate.dto.CandidatePoolRowResponse;
import app.lightmove.api.candidate.dto.CandidatePoolViewCountsResponse;
import app.lightmove.api.candidate.dto.CandidatePipelineStaffResponse;
import app.lightmove.api.candidate.dto.CandidatePipelineStaffRowResponse;
import app.lightmove.api.candidate.dto.MapPeopleToPositionResponse;
import app.lightmove.api.candidate.dto.PersonPositionResponse;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.PersonTimelineEntryResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.candidate.model.PoolPersonExport;
import app.lightmove.api.candidate.model.PoolPositionExport;
import app.lightmove.api.candidate.model.PoolViewCounts;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.CandidateTagRepository;
import app.lightmove.api.candidate.repository.PersonPoolQuery;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workspace's people as a CRM, outside any one mandate (decision D2): the Candidates page's read,
 * and the team's own facts about a person — who owns the relationship, whether they may be approached,
 * and how the team labels them. Each change is a timeline line and an audit event; none of it rides
 * {@code CandidateResponse}, which a client seat reads.
 */
@Service
@RequiredArgsConstructor
public class CandidatePoolService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String PERSON_TARGET = "person";

    private final PersonRepository people;
    private final PersonPoolQuery pool;
    private final CandidateRepository candidates;
    private final CandidateTagRepository tagCatalog;
    private final CandidateTagService tags;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final WorkspaceAccess workspaceAccess;
    private final PersonRecordService records;
    private final CandidateService mandates;
    private final ProjectAccess projectAccess;
    private final PersonTimelineService timeline;
    private final PersonActivityRecorder activity;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public CandidatePoolResponse list(UUID userId, UUID workspaceId, PoolCriteria criteria, Integer page,
                                      Integer size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageIndex = page == null ? 0 : Math.max(page, 0);
        List<UUID> ids = pool.page(workspaceId, userId, criteria, pageIndex * pageSize, pageSize);
        PoolViewCounts counts = pool.viewCounts(workspaceId, userId, criteria);
        long total = switch (criteria.view()) {
            case ALL -> counts.all();
            case MINE -> counts.mine();
            case ACTIVE -> counts.active();
            case UNPLACED -> counts.unplaced();
        };
        return new CandidatePoolResponse(rowsOf(workspaceId, ids), total,
                new CandidatePoolViewCountsResponse(counts.all(), counts.mine(), counts.active(), counts.unplaced()),
                counts.pool(), pool.countries(workspaceId));
    }

    @Transactional(readOnly = true)
    public long sizeOf(UUID workspaceId) {
        return people.countByWorkspaceId(workspaceId);
    }

    /**
     * Every person the query matches, or exactly those named, for a file. Past {@code cap} it is refused
     * rather than cut short, as the Companies export is: half a file still looks whole.
     */
    @Transactional(readOnly = true)
    public List<PoolPersonExport> exportOf(UUID userId, UUID workspaceId, PoolCriteria criteria,
                                           List<UUID> personIds, int cap) {
        List<UUID> ids = personIds == null || personIds.isEmpty()
                ? pool.all(workspaceId, userId, criteria, cap)
                : personIds.stream().distinct().toList();
        if (ids.size() > cap) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That is more people than one file may carry. Narrow the filters and export again.");
        }
        Map<UUID, Person> persons = personsOf(workspaceId, ids);
        Map<UUID, List<Candidate>> mapped = mappingsOf(workspaceId, persons.values());
        Map<UUID, Project> mandates = projectsOf(mapped);
        Map<UUID, String> tagLabels = tagCatalog.findByWorkspaceIdAndIdIn(workspaceId,
                        persons.values().stream().flatMap(person -> person.getTagIds().stream()).distinct().toList())
                .stream().collect(Collectors.toMap(CandidateTag::getId, CandidateTag::getLabel));
        Map<UUID, User> owners = usersOf(persons.values().stream().map(Person::getOwnerUserId).toList());
        return ids.stream().map(persons::get).filter(Objects::nonNull).map(person -> {
            List<Candidate> rows = mapped.getOrDefault(person.getId(), List.of());
            User owner = person.getOwnerUserId() == null ? null : owners.get(person.getOwnerUserId());
            return new PoolPersonExport(person.getFullName(), person.getTitle(),
                    PersonRecordService.employerOf(person, rows), person.getLocationCity(),
                    person.getLocationCountry(), person.getLinkedinUrl(),
                    person.emailContacts().stream().map(contact -> contact.getValue()).toList(),
                    person.phoneContacts().stream().map(contact -> contact.getValue()).toList(),
                    rows.stream().map(row -> {
                        Project mandate = mandates.get(row.getProjectId());
                        return new PoolPositionExport(mandate == null ? null : mandate.getPositionTitle(),
                                row.getStatus().value());
                    }).toList(),
                    person.getTagIds().stream().map(tagLabels::get).filter(Objects::nonNull).sorted().toList(),
                    owner == null ? null : owner.getFullName(), person.isDoNotContact(), person.getCreatedAt());
        }).toList();
    }

    @Transactional
    public PersonRecordResponse assignOwner(UUID userId, UUID workspaceId, UUID personId, UUID ownerUserId,
                                            HttpServletRequest httpRequest) {
        refuseNonStaffOwner(workspaceId, ownerUserId);
        Person person = people.requireInWorkspace(personId, workspaceId);
        reassign(userId, person, ownerUserId, httpRequest);
        return records.toRecord(userId, person);
    }

    @Transactional
    public BulkPeopleChangeResponse assignOwner(UUID userId, UUID workspaceId, List<UUID> personIds,
                                                UUID ownerUserId, HttpServletRequest httpRequest) {
        refuseNonStaffOwner(workspaceId, ownerUserId);
        int changed = 0;
        for (Person person : requirePeople(workspaceId, personIds)) {
            changed += reassign(userId, person, ownerUserId, httpRequest) ? 1 : 0;
        }
        return new BulkPeopleChangeResponse(changed);
    }

    @Transactional
    public PersonRecordResponse markDoNotContact(UUID userId, UUID workspaceId, UUID personId, boolean doNotContact,
                                                 String reason, HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        boolean changed = doNotContact ? person.markDoNotContact(why, userId) : person.clearDoNotContact();
        if (changed) {
            activity.record(person, null, userId, doNotContact ? PersonActivityKind.DO_NOT_CONTACT_SET
                    : PersonActivityKind.DO_NOT_CONTACT_CLEARED, PersonActivityDetails.none());
            audit.event(doNotContact ? ProjectEventType.PERSON_DO_NOT_CONTACT_SET
                            : ProjectEventType.PERSON_DO_NOT_CONTACT_CLEARED)
                    .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                    .record();
        }
        return records.toRecord(userId, person);
    }

    @Transactional
    public PersonRecordResponse tag(UUID userId, UUID workspaceId, UUID personId, UUID tagId,
                                    HttpServletRequest httpRequest) {
        CandidateTag tag = tags.requireOfferable(workspaceId, List.of(tagId)).getFirst();
        Person person = people.requireInWorkspace(personId, workspaceId);
        retag(userId, person, tag, false, httpRequest);
        return records.toRecord(userId, person);
    }

    @Transactional
    public PersonRecordResponse untag(UUID userId, UUID workspaceId, UUID personId, UUID tagId,
                                      HttpServletRequest httpRequest) {
        CandidateTag tag = tags.requireOwned(workspaceId, List.of(tagId)).getFirst();
        Person person = people.requireInWorkspace(personId, workspaceId);
        retag(userId, person, tag, true, httpRequest);
        return records.toRecord(userId, person);
    }

    @Transactional
    public BulkPeopleChangeResponse retag(UUID userId, UUID workspaceId, List<UUID> personIds, List<UUID> tagIds,
                                          boolean remove, HttpServletRequest httpRequest) {
        List<CandidateTag> chosen = remove ? tags.requireOwned(workspaceId, tagIds)
                : tags.requireOfferable(workspaceId, tagIds);
        int changed = 0;
        for (Person person : requirePeople(workspaceId, personIds)) {
            boolean touched = false;
            for (CandidateTag tag : chosen) {
                touched |= retag(userId, person, tag, remove, httpRequest);
            }
            changed += touched ? 1 : 0;
        }
        return new BulkPeopleChangeResponse(changed);
    }

    /**
     * Adds the people named to a position, as Identified. Reading the pool is any staff member's, but
     * filing someone onto a mandate is work on it, so the caller needs that mandate's seat.
     */
    @Transactional
    public MapPeopleToPositionResponse mapToPosition(UUID userId, UUID workspaceId, UUID projectId,
                                                     List<UUID> personIds, HttpServletRequest httpRequest) {
        projectAccess.requireAction(userId, workspaceId, projectId, ProjectAction.WORK_EXECUTE);
        return mandates.mapFromPool(userId, workspaceId, projectId, requirePeople(workspaceId, personIds),
                httpRequest);
    }

    private boolean reassign(UUID userId, Person person, UUID ownerUserId, HttpServletRequest httpRequest) {
        if (!person.assignOwner(ownerUserId)) {
            return false;
        }
        activity.record(person, null, userId, PersonActivityKind.OWNER_CHANGED,
                PersonActivityDetails.of("ownerUserId", ownerUserId));
        audit.event(ProjectEventType.PERSON_OWNER_CHANGED).actor(userId).workspace(person.getWorkspaceId())
                .target(PERSON_TARGET, person.getId()).from(httpRequest)
                .detailIfPresent("ownerUserId", ownerUserId == null ? null : ownerUserId.toString())
                .record();
        return true;
    }

    private boolean retag(UUID userId, Person person, CandidateTag tag, boolean remove,
                          HttpServletRequest httpRequest) {
        boolean changed = remove ? person.untag(tag.getId()) : person.tag(tag.getId());
        if (!changed) {
            return false;
        }
        activity.record(person, null, userId, remove ? PersonActivityKind.UNTAGGED : PersonActivityKind.TAGGED,
                PersonActivityDetails.of("tagId", tag.getId()).and("tag", tag.getLabel()));
        audit.event(remove ? ProjectEventType.PERSON_UNTAGGED : ProjectEventType.PERSON_TAGGED)
                .actor(userId).workspace(person.getWorkspaceId()).target(PERSON_TARGET, person.getId())
                .from(httpRequest)
                .detail("tagId", tag.getId().toString())
                .record();
        return true;
    }

    private void refuseNonStaffOwner(UUID workspaceId, UUID ownerUserId) {
        if (ownerUserId != null && !workspaceAccess.isStaff(ownerUserId, workspaceId)) {
            throw ApiException.of(ErrorCode.PERSON_OWNER_NOT_STAFF);
        }
    }

    /**
     * The staff columns of the position's Candidates page, for the rows a page of it drew. Ids that are
     * not this mandate's are dropped rather than refused: the page asks for what it was just shown.
     */
    @Transactional(readOnly = true)
    public CandidatePipelineStaffResponse pipelineOverlayOf(UUID workspaceId, UUID projectId,
                                                           List<UUID> candidateIds) {
        projects.requireInWorkspace(projectId, workspaceId);
        List<UUID> distinct = candidateIds == null ? List.of() : candidateIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return new CandidatePipelineStaffResponse(List.of());
        }
        int maxRows = mandates.maxPageSize();
        if (distinct.size() > maxRows) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "candidateId may name " + maxRows + " rows at most");
        }
        List<Candidate> shown = candidates.findByProjectIdAndIdIn(projectId, distinct);
        List<Person> persons = shown.stream().map(Candidate::getPerson).distinct().toList();
        Map<UUID, List<Candidate>> mapped = mappingsOf(workspaceId, persons);
        Map<UUID, Project> mandates = projectsOf(mapped);
        Map<UUID, PersonTimelineEntryResponse> latest = timeline.latestOf(workspaceId, persons);
        Map<UUID, User> named = users.findAllById(shown.stream()
                        .flatMap(row -> Stream.of(row.getAddedBy(), row.getPerson().getDoNotContactSetBy()))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return new CandidatePipelineStaffResponse(shown.stream().map(row -> {
            Person person = row.getPerson();
            List<Candidate> elsewhere = mapped.getOrDefault(person.getId(), List.of()).stream()
                    .filter(other -> !other.getProjectId().equals(projectId))
                    .toList();
            User filer = named.get(row.getAddedBy());
            return new CandidatePipelineStaffRowResponse(row.getId(), List.copyOf(person.getTagIds()),
                    chipsOf(elsewhere, mandates), row.getAddedBy(), filer == null ? null : filer.getFullName(),
                    PersonRecordService.doNotContactOf(person, named), latest.get(person.getId()));
        }).toList());
    }

    /** Every person named, all of them the caller's workspace's; one stranger's id refuses the batch. */
    private List<Person> requirePeople(UUID workspaceId, List<UUID> personIds) {
        List<UUID> distinct = personIds.stream().distinct().toList();
        List<Person> found = people.findByWorkspaceIdAndIdIn(workspaceId, distinct);
        if (found.size() != distinct.size()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return found;
    }

    private List<CandidatePoolRowResponse> rowsOf(UUID workspaceId, List<UUID> ids) {
        Map<UUID, Person> persons = personsOf(workspaceId, ids);
        Map<UUID, List<Candidate>> mapped = mappingsOf(workspaceId, persons.values());
        Map<UUID, Project> mandates = projectsOf(mapped);
        Map<UUID, PersonTimelineEntryResponse> latest = timeline.latestOf(workspaceId, persons.values());
        return ids.stream().map(persons::get).filter(Objects::nonNull).map(person -> {
            List<Candidate> rows = mapped.getOrDefault(person.getId(), List.of());
            return new CandidatePoolRowResponse(person.getId(), person.getFullName(), person.getTitle(),
                    PersonRecordService.employerOf(person, rows), person.getLocationCity(),
                    person.getLocationCountry(), person.getLinkedinUrl(), person.getProfile().enrichedAt(),
                    person.isDoNotContact(), person.getYearsExperience(), person.getProfile().career().size(),
                    holds(person, ContactChannel.EMAIL), holds(person, ContactChannel.PHONE),
                    chipsOf(rows, mandates), List.copyOf(person.getTagIds()), person.getOwnerUserId(),
                    latest.get(person.getId()));
        }).toList();
    }

    private static boolean holds(Person person, ContactChannel channel) {
        return person.getContacts().stream().anyMatch(contact -> contact.getChannel() == channel);
    }

    /** Newest first. A chip is what the row draws, so who filed it and whether the caller may work it are left out. */
    private static List<PersonPositionResponse> chipsOf(List<Candidate> rows, Map<UUID, Project> mandates) {
        return rows.stream()
                .sorted(Comparator.comparing(Candidate::getCreatedAt).reversed())
                .map(row -> {
                    Project mandate = mandates.get(row.getProjectId());
                    return new PersonPositionResponse(row.getId(), row.getProjectId(),
                            mandate == null ? null : mandate.getPositionTitle(), row.getStatus().value(),
                            row.getAddedBy(), null, row.getCreatedAt(), row.getSource().value(), false);
                })
                .toList();
    }

    private Map<UUID, Person> personsOf(UUID workspaceId, List<UUID> ids) {
        return ids.isEmpty() ? Map.of() : people.findByWorkspaceIdAndIdIn(workspaceId, ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
    }

    /** Each person's mappings, oldest first, as {@link PersonRecordService#employerOf} reads them. */
    private Map<UUID, List<Candidate>> mappingsOf(UUID workspaceId, Collection<Person> persons) {
        if (persons.isEmpty()) {
            return Map.of();
        }
        return candidates.findPositionsOfPeople(workspaceId, persons.stream().map(Person::getId).toList()).stream()
                .collect(Collectors.groupingBy(row -> row.getPerson().getId()));
    }

    private Map<UUID, Project> projectsOf(Map<UUID, List<Candidate>> mapped) {
        List<UUID> ids = mapped.values().stream().flatMap(List::stream).map(Candidate::getProjectId).distinct().toList();
        return ids.isEmpty() ? Map.of() : projects.findAllById(ids).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private Map<UUID, User> usersOf(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of() : users.findAllById(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
