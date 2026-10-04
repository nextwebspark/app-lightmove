package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.TimelineGroup;
import app.lightmove.api.candidate.dto.PersonTimelineEntryResponse;
import app.lightmove.api.candidate.dto.PersonTimelineResponse;
import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonActivity;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.model.PersonDocument;
import app.lightmove.api.candidate.repository.CandidateTagRepository;
import app.lightmove.api.candidate.repository.PersonDocumentRepository;
import app.lightmove.api.candidate.repository.PersonActivityRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads {@code app_lm_person_activity} back: one person's history, or the workspace's feed. Staff-only
 * like the notes, and an allowlist like {@code ProjectActivityService}'s — a detail key nobody listed
 * here never reaches a screen, whatever a writer later puts in it.
 */
@Service
@RequiredArgsConstructor
public class PersonTimelineService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    private static final int EXCERPT_LENGTH = 140;
    private static final Set<String> DETAIL_KEYS = Set.of("door", "backgroundConfirmed", "from", "to", "vendor",
            "runId", "emails", "phones", "channel", "found", "via", "noteId", "kind", "tagId", "tag",
            "ownerUserId", "owner", "documentId", "version", "category", "versions");
    private static final Set<PersonActivityKind> NOTE_KINDS = TimelineGroup.NOTES.kinds();
    private static final Set<PersonActivityKind> DOCUMENT_KINDS = TimelineGroup.DOCUMENTS.kinds();
    /** Bound when a filter is open, so the parameter is never an untyped null; matches no row. */
    private static final UUID NOBODY = new UUID(0, 0);
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

    private final PersonRepository people;
    private final PersonActivityRepository activity;
    private final PersonNoteRepository notes;
    private final UserRepository users;
    private final CandidateTagRepository tags;
    private final PersonDocumentRepository documents;

    @Transactional(readOnly = true)
    public PersonTimelineResponse timelineOf(UUID workspaceId, UUID personId, String group, Long before,
                                             Integer limit) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        int size = sizeOf(limit);
        List<PersonActivity> page = activity.findPersonTimeline(workspaceId, personId, cursorOf(before),
                TimelineGroup.kindsOf(group), Limit.of(size + 1));
        return pageOf(workspaceId, page, size, Map.of(person.getId(), person));
    }

    /** The workspace's feed; each filter left null matches everything. */
    @Transactional(readOnly = true)
    public PersonTimelineResponse feedOf(UUID workspaceId, UUID actor, UUID projectId, String group,
                                         Instant from, Instant to, Long before, Integer limit) {
        int size = sizeOf(limit);
        List<PersonActivity> page = activity.findWorkspaceFeed(workspaceId, cursorOf(before),
                TimelineGroup.kindsOf(group),
                actor == null, actor == null ? NOBODY : actor,
                projectId == null, projectId == null ? NOBODY : projectId,
                from == null ? Instant.EPOCH : from, to == null ? FAR_FUTURE : to,
                Limit.of(size + 1));
        List<UUID> personIds = page.stream().map(PersonActivity::getPersonId).distinct().toList();
        Map<UUID, Person> persons = people.findByWorkspaceIdAndIdIn(workspaceId, personIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        return pageOf(workspaceId, page, size, persons);
    }

    /** Each person's latest line, for the Candidates page's Last activity column. */
    @Transactional(readOnly = true)
    public Map<UUID, PersonTimelineEntryResponse> latestOf(UUID workspaceId, Collection<Person> persons) {
        if (persons.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Person> byId = persons.stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        return entriesOf(workspaceId, activity.findLatestOfPeople(workspaceId, byId.keySet()), byId).stream()
                .collect(Collectors.toMap(PersonTimelineEntryResponse::personId, Function.identity()));
    }

    private PersonTimelineResponse pageOf(UUID workspaceId, List<PersonActivity> page, int size,
                                          Map<UUID, Person> persons) {
        boolean hasMore = page.size() > size;
        List<PersonActivity> shown = hasMore ? page.subList(0, size) : page;
        return new PersonTimelineResponse(entriesOf(workspaceId, shown, persons),
                hasMore ? shown.getLast().getId() : null);
    }

    private List<PersonTimelineEntryResponse> entriesOf(UUID workspaceId, List<PersonActivity> shown,
                                                        Map<UUID, Person> persons) {
        Map<UUID, User> named = users.findAllById(shown.stream()
                        .flatMap(line -> Stream.of(line.getActorUserId(), idIn(line, "ownerUserId")))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, PersonNote> liveNotes = notes.findByWorkspaceIdAndIdIn(workspaceId, shown.stream()
                        .map(PersonTimelineService::noteIdOf).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PersonNote::getId, Function.identity()));
        Map<UUID, CandidateTag> liveTags = tags.findByWorkspaceIdAndIdIn(workspaceId, shown.stream()
                        .map(line -> idIn(line, "tagId")).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(CandidateTag::getId, Function.identity()));
        Map<UUID, PersonDocument> liveDocuments = documents.findByWorkspaceIdAndIdIn(workspaceId, shown.stream()
                        .map(PersonTimelineService::documentIdOf).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PersonDocument::getId, Function.identity()));

        return shown.stream().map(line -> {
            User actor = line.getActorUserId() == null ? null : named.get(line.getActorUserId());
            Person person = persons.get(line.getPersonId());
            UUID noteId = noteIdOf(line);
            PersonNote note = noteId == null ? null : liveNotes.get(noteId);
            return new PersonTimelineEntryResponse(line.getId(), line.getKind().name(), line.getOccurredAt(),
                    line.getActorUserId(), actor == null ? null : actor.getFullName(),
                    actor == null ? null : actor.getAvatarUrl(),
                    line.getPersonId(), person == null ? null : person.getFullName(),
                    line.getProjectId(), line.getProjectTitle(), detailsOf(line, named, liveTags, liveDocuments),
                    note == null ? null : excerptOf(note.getBody()));
        }).toList();
    }

    /**
     * A tag reads as it is spelled today, an owner by their name today and a document by its title while
     * it exists; the line keeps only the ids, so a removed document leaves no name behind.
     */
    private static Map<String, String> detailsOf(PersonActivity line, Map<UUID, User> named,
                                                 Map<UUID, CandidateTag> liveTags,
                                                 Map<UUID, PersonDocument> liveDocuments) {
        Map<String, String> shown = new LinkedHashMap<>();
        line.getDetails().forEach((key, value) -> {
            if (DETAIL_KEYS.contains(key) && value != null) {
                shown.put(key, String.valueOf(value));
            }
        });
        UUID tagId = idIn(line, "tagId");
        if (tagId != null && liveTags.containsKey(tagId)) {
            shown.put("tag", liveTags.get(tagId).getLabel());
        }
        UUID ownerId = idIn(line, "ownerUserId");
        if (ownerId != null && named.containsKey(ownerId)) {
            shown.put("owner", named.get(ownerId).getFullName());
        }
        UUID documentId = documentIdOf(line);
        if (documentId != null && liveDocuments.containsKey(documentId)) {
            shown.put("document", liveDocuments.get(documentId).getTitle());
        }
        return shown;
    }

    private static UUID noteIdOf(PersonActivity line) {
        return NOTE_KINDS.contains(line.getKind()) ? idIn(line, "noteId") : null;
    }

    private static UUID documentIdOf(PersonActivity line) {
        return DOCUMENT_KINDS.contains(line.getKind()) ? idIn(line, "documentId") : null;
    }

    private static UUID idIn(PersonActivity line, String key) {
        Object id = line.getDetails().get(key);
        try {
            return id == null ? null : UUID.fromString(id.toString());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static String excerptOf(String body) {
        String flat = body.strip().replaceAll("\\s+", " ");
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH - 1).strip() + "…";
    }

    private static int sizeOf(Integer limit) {
        return limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
    }

    private static long cursorOf(Long before) {
        return before == null ? Long.MAX_VALUE : before;
    }
}
